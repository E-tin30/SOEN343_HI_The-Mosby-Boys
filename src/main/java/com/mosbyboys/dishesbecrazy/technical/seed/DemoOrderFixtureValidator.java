package com.mosbyboys.dishesbecrazy.technical.seed;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;
import com.mosbyboys.dishesbecrazy.domain.ord.state.PlacedState;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.Account;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.EventSeed;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.MenuSeed;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.OrderSeed;

// Checks a single fixture snapshot; it has no database, Auth or application-state dependencies.
final class DemoOrderFixtureValidator {
    private final Map<String, Account> accounts;
    private final Map<UUID, MenuSeed> menu;

    DemoOrderFixtureValidator(DemoFixtures catalogue) {
        accounts = catalogue.accounts().stream().collect(Collectors.toMap(Account::username, Function.identity()));
        menu = catalogue.menuItems().stream().collect(Collectors.toMap(MenuSeed::id, Function.identity()));
    }

    void validate(OrderSeed[] orders) {
        Assert.isTrue(orders != null && orders.length > 0, "Demo orders are required.");
        var orderIds = new HashSet<UUID>();
        var lineIds = new HashSet<UUID>();
        var eventIds = new HashSet<UUID>();
        for (OrderSeed order : orders) {
            Assert.isTrue(order != null && order.id() != null && orderIds.add(order.id()),
                    "Demo orders require unique IDs.");
            requireRole(order.customerUsername(), Role.CUSTOMER);
            requireRole(order.restaurantUsername(), Role.RESTAURANT);
            if (order.courierUsername() != null) {
                requireRole(order.courierUsername(), Role.COURIER);
            }
            Assert.notNull(order.placedAt(), "Order placement time is required.");
            Assert.hasText(order.state(), "Order state is required.");
            OrderStates.fromName(order.state());
            Assert.isTrue("RejectedState".equals(order.state())
                            ? order.rejectionReason() != null && !order.rejectionReason().isBlank()
                            : order.rejectionReason() == null,
                    "Only rejected orders require a rejection reason.");
            validateLines(order, lineIds);
            validateHistory(order, eventIds);
        }
    }
    // IDs must be unique across all orders in each target table, not only within one order.

    private void validateLines(OrderSeed order, Set<UUID> ids) {
        Assert.notEmpty(order.lines(), "Every demo order requires purchased items.");
        BigDecimal total = BigDecimal.ZERO;
        for (var line : order.lines()) {
            Assert.isTrue(line != null && line.id() != null && ids.add(line.id()),
                    "Order lines require unique IDs.");
            MenuSeed item = menu.get(line.menuItemId());
            Assert.isTrue(item != null && item.restaurantUsername().equals(order.restaurantUsername()),
                    "Order lines must reference their restaurant's menu.");
            Assert.hasText(line.menuItemName(), "Purchased item name is required.");
            Assert.isTrue(line.quantity() > 0 && line.unitPrice() != null && line.unitPrice().signum() > 0,
                    "Purchased quantities and prices must be positive.");
            total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        Assert.isTrue(order.subtotal() != null && total.compareTo(order.subtotal()) == 0,
                "Order subtotal must equal the sum of its purchased items.");
    }
    // Historical prices are snapshots; current menu prices and remaining stock are not rewritten or deducted.

    private void validateHistory(OrderSeed order, Set<UUID> ids) {
        Assert.notEmpty(order.events(), "Every demo order requires its event history.");
        OrderState state = null;
        Instant previousTime = null;
        boolean pickedUp = false;
        for (EventSeed event : order.events()) {
            Assert.isTrue(event != null && event.id() != null && ids.add(event.id()),
                    "Order events require unique IDs.");
            Assert.isTrue(event.createdAt() != null && (previousTime == null
                            ? event.createdAt().equals(order.placedAt()) : event.createdAt().isAfter(previousTime)),
                    "Events must start at placement and have strictly increasing timestamps.");
            Assert.isTrue(Objects.equals(state == null ? null : state.toString(), event.previousState()),
                    "Each event must continue from the previous event's state.");
            validateActor(order, event);
            state = transition(state, event.action());
            Assert.isTrue(state.toString().equals(event.newState()), "Event action and new state disagree.");
            previousTime = event.createdAt();
            pickedUp |= "PICKUP".equals(event.action());
        }
        Assert.isTrue(state.toString().equals(order.state()), "Event history must end at the order's current state.");
        // A READY order may already be accepted by its courier (a stalled delivery the operator can reassign)
        Assert.isTrue(pickedUp || order.courierUsername() == null || "ReadyState".equals(order.state()),
                "A seeded courier reference requires pickup history or a READY order.");
    }
    // Replays domain transitions rather than maintaining a second list of legal state changes.

    private void validateActor(OrderSeed order, EventSeed event) {
        Assert.hasText(event.action(), "Event action is required.");
        String expected = switch (event.action()) {
            case "PLACE" -> order.customerUsername();
            case "ACCEPT", "PREPARING", "READY", "REJECT" -> order.restaurantUsername();
            case "PICKUP", "DELIVER" -> order.courierUsername();
            case "REFUND" -> {
                requireRole(event.performedByUsername(), Role.OPERATOR);
                yield event.performedByUsername();
            }
            case "CANCEL" -> {
                Account actor = accounts.get(event.performedByUsername());
                Assert.isTrue(actor != null && (actor.role() == Role.OPERATOR
                                || ("PlacedState".equals(event.previousState())
                                    && actor.username().equals(order.customerUsername()))),
                        "Cancellation requires the placing customer or an operator.");
                yield event.performedByUsername();
            }
            default -> throw new IllegalArgumentException("Unknown demo event action: " + event.action());
        };
        Assert.isTrue(expected != null && expected.equals(event.performedByUsername()),
                "Event actor must match the order's customer, restaurant or courier.");
    }
    // Historical actors must own the order action; their current availability is unrelated to past events.

    private OrderState transition(OrderState state, String action) {
        if (state == null) {
            Assert.isTrue("PLACE".equals(action), "Order history must begin with PLACE.");
            return new PlacedState();
        }
        return switch (action) {
            case "ACCEPT" -> state.accept();
            case "PREPARING" -> state.startPreparing();
            case "READY" -> state.markReady();
            case "PICKUP" -> state.pickUp();
            case "DELIVER" -> state.deliver();
            case "REFUND" -> state.refund();
            case "REJECT" -> state.reject();
            case "CANCEL" -> state.cancel();
            default -> throw new IllegalArgumentException("Unknown or repeated demo event action: " + action);
        };
    }

    private void requireRole(String username, Role role) {
        Account account = accounts.get(username);
        Assert.isTrue(account != null && account.role() == role,
                "Demo order requires an existing " + role + " account: " + username);
    }
}
