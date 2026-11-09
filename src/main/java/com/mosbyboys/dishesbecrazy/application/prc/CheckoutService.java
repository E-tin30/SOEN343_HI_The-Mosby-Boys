package com.mosbyboys.dishesbecrazy.application.prc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import com.mosbyboys.dishesbecrazy.application.ord.MenuService;
import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.application.ord.PermissionDeniedException;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.technical.payment.StripePayments;
import com.mosbyboys.dishesbecrazy.technical.repository.CheckoutRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.CheckoutEntity;
import com.stripe.model.PaymentIntent;

import tools.jackson.databind.json.JsonMapper;

@Service
public class CheckoutService {
    private final CheckoutRepo checkouts;
    private final StripePayments payments;
    private final ORDFacade orders;
    private final REGFacade users;
    private final MenuService menu;
    private final PRCFacade pricing;
    private final JsonMapper json;
    private final TransactionTemplate transaction;

    public CheckoutService(CheckoutRepo checkouts, StripePayments payments, ORDFacade orders, REGFacade users,
                           MenuService menu, PRCFacade pricing, JsonMapper json, PlatformTransactionManager manager) {
        this.checkouts = checkouts;
        this.payments = payments;
        this.orders = orders;
        this.users = users;
        this.menu = menu;
        this.pricing = pricing;
        this.json = json;
        this.transaction = new TransactionTemplate(manager);
    }

    public record Item(UUID menuItemId, int quantity) { }
    public record Line(UUID menuItemId, int quantity, BigDecimal unitPrice, String menuItemName) {
        OrderLine toOrderLine() {
            return new OrderLine(menuItemId, quantity, unitPrice, menuItemName);
        }
    }
    public record Start(UUID checkoutId, String clientSecret, UUID orderId, long totalMinor, boolean cancelled) {
        @Override public String toString() {
            return "CheckoutStart[checkoutId=" + checkoutId + ", clientSecret=[redacted], orderId=" + orderId + "]";
        }
    }
    // Displays the stored CAD amount and redacts the client secret from the record's string representation.
    private record Completion(Order order, String rejection) { }
    // Stored lines freeze server-derived names/prices; browser-supplied prices never determine the charge.

    public PricingBreakdown quote(User customer, UUID restaurantId, List<Item> items) {
        var lines = currentLines(restaurantId, selection(items));
        orders.validatePlacement(customer, restaurantId, lines.stream().map(Line::toOrderLine).toList());
        return pricing.getTotal(lines.stream().map(Line::toOrderLine).toList());
    }

    public Start start(User customer, UUID checkoutId, UUID restaurantId, List<Item> items) {
        requireCustomer(customer);
        Assert.notNull(checkoutId, "Checkout ID is required.");
        var selection = selection(items);
        CheckoutEntity checkout = checkouts.findById(checkoutId).orElse(null);
        if (checkout == null) {
            Assert.isTrue(!orders.orderExists(checkoutId), "Checkout ID is already used by an order.");
            var lines = currentLines(restaurantId, selection);
            var domainLines = lines.stream().map(Line::toOrderLine).toList();
            orders.validatePlacement(customer, restaurantId, domainLines);
            var total = pricing.getTotal(domainLines);
            var proposed = new CheckoutEntity();
            proposed.setId(checkoutId);
            proposed.setCustomerId(customer.getUserId());
            proposed.setRestaurantId(restaurantId);
            proposed.setLinesJson(json.writeValueAsString(lines));
            proposed.setSubtotal(total.subtotal());
            proposed.setTotalMinor(minorUnits(total.total()));
            proposed.setCreatedAt(Instant.now());
            Assert.isTrue(proposed.getTotalMinor() >= 50, "Payment must be at least $0.50 CAD.");
            try {
                checkout = transaction.execute(status -> checkouts.saveAndFlush(proposed));
            } catch (DataIntegrityViolationException error) {
                checkout = checkouts.findById(checkoutId).orElseThrow(() -> error);
            }
        }
        requireOwner(checkout, customer.getUserId());
        Assert.isTrue(Objects.equals(checkout.getRestaurantId(), restaurantId)
                        && storedLines(checkout).stream().map(line -> new Item(line.menuItemId(), line.quantity())).toList().equals(selection),
                "This checkout belongs to a different cart. Return to the cart and start a new checkout.");
        if (checkout.getFailureReason() != null) {
            return new Start(checkout.getId(), null, null, checkout.getTotalMinor(), true);
        }
        if (checkout.isCompleted()) {
            return new Start(checkout.getId(), null, checkout.getId(), checkout.getTotalMinor(), false);
        }
        // Persist the immutable snapshot before Stripe; duplicate startup requests cannot replace it.

        final var preparedIntent = checkout.getPaymentIntentId() == null
                ? payments.create(checkout.getId(), checkout.getCustomerId(), checkout.getTotalMinor())
                : payments.retrieve(checkout.getPaymentIntentId());
        UUID id = checkout.getId();
        String intentId = transaction.execute(status -> {
            var current = locked(id);
            if (current.getPaymentIntentId() == null) {
                current.setPaymentIntentId(preparedIntent.getId());
            }
            return current.getPaymentIntentId();
        });
        var intent = preparedIntent;
        if (!intent.getId().equals(intentId)) {
            payments.cancel(intent.getId(), id);
            intent = payments.retrieve(intentId);
        }
        return new Start(id, intent.getClientSecret(), null, checkout.getTotalMinor(), "canceled".equals(intent.getStatus()));
    }
    // Stripe creation is outside the SQL transaction; a failed response can reuse the stored checkout and idempotency key.

    public Order complete(UUID checkoutId, UUID customerId) {
        Completion result = transaction.execute(status -> finish(checkoutId, customerId));
        if (result.rejection() != null) {
            throw new IllegalArgumentException(result.rejection());
        }
        return result.order();
    }

    public void completeFromWebhook(UUID checkoutId) {
        transaction.execute(status -> finish(checkoutId, null));
    }
    // The caller uses JWT ownership; only the separately verified Stripe webhook can omit the customer ID.

    public Order cancel(UUID checkoutId, UUID customerId) {
        Completion result = transaction.execute(status -> {
            var checkout = locked(checkoutId);
            requireOwner(checkout, customerId);
            if (checkout.isCompleted()) {
                return new Completion(orders.getOrder(checkoutId), null);
            }
            if (checkout.getFailureReason() != null) {
                return new Completion(null, checkout.getFailureReason());
            }
            Assert.hasText(checkout.getPaymentIntentId(), "Payment setup has not completed. Retry checkout.");
            var intent = payments.retrieve(checkout.getPaymentIntentId());
            verifyPayment(checkout, intent);
            if ("succeeded".equals(intent.getStatus())) {
                return finish(checkoutId, customerId);
            }
            if (!"canceled".equals(intent.getStatus())) {
                payments.cancel(intent.getId(), checkoutId);
            }
            return reject(checkout, "Checkout cancelled. Start a new checkout to order again.");
        });
        return result.order();
    }
    // Leaving checkout releases a known authorization; an already-captured payment resolves to its existing order instead.

    private Completion finish(UUID id, UUID customerId) {
        var checkout = locked(id);
        if (customerId != null) {
            requireOwner(checkout, customerId);
        }
        if (checkout.isCompleted()) {
            return new Completion(orders.getOrder(id), null);
        }
        if (checkout.getFailureReason() != null) {
            return new Completion(null, checkout.getFailureReason());
        }
        Assert.hasText(checkout.getPaymentIntentId(), "Payment setup has not completed. Retry checkout.");
        var intent = payments.retrieve(checkout.getPaymentIntentId());
        verifyPayment(checkout, intent);
        var lines = storedLines(checkout).stream().map(Line::toOrderLine).toList();
        boolean authorized = "requires_capture".equals(intent.getStatus());
        if ("canceled".equals(intent.getStatus())) {
            return reject(checkout, "Payment was cancelled. Return to the cart and start a new checkout.");
        }
        if (!authorized && !"succeeded".equals(intent.getStatus())) {
            throw new IllegalStateException("Payment is not authorized yet. Complete the payment form first.");
        }
        if (authorized) {
            try {
                orders.validatePlacement(users.getCurrentUser(checkout.getCustomerId()), checkout.getRestaurantId(), lines);
                Assert.isTrue(minorUnits(pricing.getTotal(lines).total()) == checkout.getTotalMinor(),
                        "Pricing changed. Return to the cart and start a new checkout.");
            } catch (IllegalArgumentException | PermissionDeniedException | NoSuchElementException error) {
                payments.cancel(intent.getId(), id);
                return reject(checkout, "The restaurant or selected items changed. Return to the cart before paying.");
            }
        }
        // An already-captured retry uses the stored, previously validated snapshot even if availability later changes.

        Order order = orders.recordCheckoutOrder(id, checkout.getCustomerId(), checkout.getRestaurantId(),
                lines, checkout.getSubtotal(), Instant.now());
        checkouts.flush();
        if (authorized) {
            intent = payments.capture(intent.getId(), id);
            verifyPayment(checkout, intent);
        }
        Assert.isTrue("succeeded".equals(intent.getStatus())
                        && Objects.equals(intent.getAmountReceived(), checkout.getTotalMinor()),
                "Payment capture has not been confirmed. Retry this checkout.");
        checkout.setCompleted(true);
        return new Completion(order, null);
    }
    // The row lock and stable order ID make callbacks/retries create one order; queues publish only after SQL commit.

    private void verifyPayment(CheckoutEntity checkout, PaymentIntent intent) {
        Assert.isTrue(intent != null && Objects.equals(intent.getId(), checkout.getPaymentIntentId())
                        && Objects.equals(intent.getAmount(), checkout.getTotalMinor()) && "cad".equals(intent.getCurrency())
                        && "manual".equals(intent.getCaptureMethod())
                        && "dishes-checkout-v1".equals(intent.getMetadata().get("integration"))
                        && checkout.getId().toString().equals(intent.getMetadata().get("checkout_id"))
                        && checkout.getCustomerId().toString().equals(intent.getMetadata().get("customer_id")),
                "Payment does not match this checkout.");
    }

    private Completion reject(CheckoutEntity checkout, String reason) {
        checkout.setFailureReason(reason);
        return new Completion(null, reason);
    }
    // Commit the cancellation outcome before returning a customer error; callbacks can acknowledge terminal failures.

    private long minorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
    // Preserves the existing CAD cent rounding while detecting amounts that cannot fit in an integer.

    private CheckoutEntity locked(UUID id) {
        Assert.notNull(id, "Checkout ID is required.");
        return checkouts.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("Checkout not found."));
    }

    private List<Line> storedLines(CheckoutEntity checkout) {
        return Arrays.asList(json.readValue(checkout.getLinesJson(), Line[].class));
    }

    private List<Line> currentLines(UUID restaurantId, List<Item> items) {
        Assert.notNull(restaurantId, "Restaurant ID is required.");
        return items.stream().map(selected -> {
            var item = menu.load(selected.menuItemId());
            Assert.isTrue(item.belongsTo(restaurantId), "Selected items must belong to this restaurant.");
            return new Line(item.getId(), selected.quantity(), item.getPrice(), item.getName());
        }).toList();
    }

    private List<Item> selection(List<Item> items) {
        Assert.notEmpty(items, "Select at least one item.");
        var ids = new HashSet<UUID>();
        for (Item item : items) {
            Assert.isTrue(item != null && item.menuItemId() != null && item.quantity() > 0 && ids.add(item.menuItemId()),
                    "Select distinct menu items with positive quantities.");
        }
        return items.stream().sorted(Comparator.comparing(Item::menuItemId)).toList();
    }
    // Canonical ordering lets a retried request match the same cart without accepting changed items or quantities.

    private void requireCustomer(User user) {
        if (user == null || user.getRole() != Role.CUSTOMER) {
            throw new PermissionDeniedException("Only a customer can start checkout.");
        }
    }

    private void requireOwner(CheckoutEntity checkout, UUID customerId) {
        if (!checkout.getCustomerId().equals(customerId)) {
            throw new PermissionDeniedException("This checkout belongs to another customer.");
        }
    }
}
