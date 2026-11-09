package com.mosbyboys.dishesbecrazy.application.prc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mosbyboys.dishesbecrazy.application.ord.*;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.*;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.technical.payment.StripePayments;
import com.mosbyboys.dishesbecrazy.technical.repository.*;
import com.stripe.model.PaymentIntent;

import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(CheckoutServiceTest.Config.class)
class CheckoutServiceTest {
    @Autowired CheckoutService service;
    @Autowired CheckoutRepo checkouts;
    @Autowired OrderRepo orderRepo;
    @Autowired OrderLineItemRepo lineRepo;
    @Autowired OrderEventRepo eventRepo;
    @Autowired StripePayments payments;
    @Autowired MenuService menu;
    @Autowired RestaurantService restaurants;
    @Autowired REGFacade users;
    @Autowired PRCFacade pricing;
    @Autowired OrderQueues queues;

    private final UUID customerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID checkoutId = UUID.randomUUID();
    private Customer customer;
    private MenuItem item;

    @BeforeEach
    void prepare() {
        eventRepo.deleteAll();
        lineRepo.deleteAll();
        orderRepo.deleteAll();
        checkouts.deleteAll();
        reset(payments, menu, restaurants, users, pricing);
        customer = new Customer(customerId, OffsetDateTime.now(), "Demo", "Customer", "demo@example.com",
                "demo", null, "Montreal", new Location(45.5, -73.5));
        item = new MenuItem(itemId, restaurantId, "Soup", new BigDecimal("10.00"), 5,
                MenuItemStatus.AVAILABLE, "", "", Instant.now());
        when(menu.load(itemId)).thenReturn(item);
        when(restaurants.load(restaurantId)).thenReturn(new Restaurant(restaurantId, OffsetDateTime.now(),
                "Demo", "Owner", "owner@example.com", "owner", null, "Kitchen", RestaurantStatus.OPEN, null));
        when(users.getCurrentUser(customerId)).thenReturn(customer);
        when(pricing.getTotal(anyList())).thenAnswer(call -> {
            List<OrderLine> lines = call.getArgument(0);
            BigDecimal subtotal = lines.stream().map(OrderLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            return breakdown(subtotal, subtotal.add(new BigDecimal("2.00")));
        });
        when(payments.create(eq(checkoutId), eq(customerId), anyLong())).thenReturn(intent("requires_payment_method"));
        when(payments.retrieve(anyString())).thenAnswer(call -> intent("requires_capture"));
        when(payments.capture(anyString(), eq(checkoutId))).thenAnswer(call -> intent("succeeded"));
    }
    // Real H2/JPA transactions exercise commit, rollback and row locking; all Auth/catalogue/Stripe calls are mocked.

    @Test
    void completedCheckoutCreatesOneOrderEventAndQueueEntryAcrossRetries() {
        start();
        assertEquals(0, orderRepo.count());
        var order = service.complete(checkoutId, customerId);
        assertEquals(checkoutId, order.getId());
        assertEquals(checkoutId, service.complete(checkoutId, customerId).getId());
        service.completeFromWebhook(checkoutId);
        assertEquals(1, orderRepo.count());
        assertEquals(1, lineRepo.count());
        assertEquals(1, eventRepo.count());
        assertEquals(1, queues.placed(restaurantId).size());
        verify(payments, times(1)).capture(anyString(), eq(checkoutId));
        assertTrue(checkouts.findById(checkoutId).orElseThrow().isCompleted());
    }

    @Test
    void openingCheckoutEarlierDoesNotBackdateThePlacedOrder() {
        start();
        var checkout = checkouts.findById(checkoutId).orElseThrow();
        checkout.setCreatedAt(Instant.EPOCH);
        checkouts.saveAndFlush(checkout);
        Instant beforePlacement = Instant.now().minusSeconds(1);

        var order = service.complete(checkoutId, customerId);

        assertFalse(order.getPlacedAt().isBefore(beforePlacement));
        Instant persistedTime = orderRepo.findById(checkoutId).orElseThrow().getCreatedAt();
        service.complete(checkoutId, customerId);
        assertEquals(persistedTime, orderRepo.findById(checkoutId).orElseThrow().getCreatedAt());
    }
    // FIFO/history use actual order placement, not the potentially much earlier opening of the payment form.

    @Test
    void captureFailureRollsBackOrdersEventsAndQueueChanges() {
        start();
        when(payments.capture(anyString(), any())).thenThrow(new IllegalStateException("Network status unknown"));
        assertThrows(IllegalStateException.class, () -> service.complete(checkoutId, customerId));
        assertEquals(0, orderRepo.count());
        assertEquals(0, eventRepo.count());
        assertEquals(0, lineRepo.count());
        assertEquals(0, queues.placed(restaurantId).size());
        assertFalse(checkouts.findById(checkoutId).orElseThrow().isCompleted());
    }

    @Test
    void capturedPaymentRecoversAfterDatabaseCommitFailsAndAvailabilityChanges() {
        start();
        var captured = new AtomicBoolean();
        when(payments.retrieve(anyString())).thenAnswer(call -> intent(captured.get() ? "succeeded" : "requires_capture"));
        when(payments.capture(anyString(), any())).thenAnswer(call -> {
            captured.set(true);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("Simulated database commit failure");
                }
            });
            return intent("succeeded");
        });
        assertThrows(IllegalStateException.class, () -> service.complete(checkoutId, customerId));
        assertEquals(0, orderRepo.count());
        assertEquals(0, queues.placed(restaurantId).size());
        item.markSoldOut();
        service.completeFromWebhook(checkoutId);
        assertEquals(checkoutId, service.complete(checkoutId, customerId).getId());
        assertEquals(1, orderRepo.count());
        assertEquals(1, eventRepo.count());
        assertEquals(1, queues.placed(restaurantId).size());
        verify(payments, times(1)).capture(anyString(), any());
    }
    // A verified captured payment completes its stored purchase, rather than charging again or rejecting it as newly sold out.

    @Test
    void unavailableItemsCancelAuthorizationWithoutCreatingAnOrder() {
        start();
        item.markSoldOut();
        assertThrows(IllegalArgumentException.class, () -> service.complete(checkoutId, customerId));
        assertEquals(0, orderRepo.count());
        assertEquals(0, queues.placed(restaurantId).size());
        assertNotNull(checkouts.findById(checkoutId).orElseThrow().getFailureReason());
        verify(payments).cancel(anyString(), eq(checkoutId));
        verify(payments, never()).capture(anyString(), any());
        service.completeFromWebhook(checkoutId);
    }

    @Test
    void changedPricingReleasesTheHoldBeforeAnyCharge() {
        start();
        when(pricing.getTotal(anyList())).thenReturn(breakdown(new BigDecimal("10.00"), new BigDecimal("13.00")));
        assertThrows(IllegalArgumentException.class, () -> service.complete(checkoutId, customerId));
        verify(payments).cancel(anyString(), any());
        verify(payments, never()).capture(anyString(), any());
        assertEquals(0, orderRepo.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"requires_payment_method", "requires_action", "processing"})
    void incompletePaymentsCannotCreateOrders(String status) {
        start();
        when(payments.retrieve(anyString())).thenReturn(intent(status));
        assertThrows(IllegalStateException.class, () -> service.complete(checkoutId, customerId));
        assertEquals(0, orderRepo.count());
        verify(payments, never()).capture(anyString(), any());
    }

    @Test
    void anotherCustomerCannotCompleteOrCancelTheCheckout() {
        start();
        clearInvocations(payments);
        assertThrows(PermissionDeniedException.class, () -> service.complete(checkoutId, UUID.randomUUID()));
        assertThrows(PermissionDeniedException.class, () -> service.cancel(checkoutId, UUID.randomUUID()));
        verifyNoInteractions(payments);
        assertEquals(0, orderRepo.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "currency", "owner", "checkout", "capture"})
    void mismatchedStripeDataCannotCreateOrders(String field) {
        start();
        var intent = intent("requires_capture");
        switch (field) {
            case "amount" -> intent.setAmount(1L);
            case "currency" -> intent.setCurrency("usd");
            case "owner" -> intent.setMetadata(Map.of("integration", "dishes-checkout-v1", "checkout_id", checkoutId.toString(), "customer_id", UUID.randomUUID().toString()));
            case "checkout" -> intent.setMetadata(Map.of("integration", "dishes-checkout-v1", "checkout_id", UUID.randomUUID().toString(), "customer_id", customerId.toString()));
            case "capture" -> intent.setCaptureMethod("automatic");
        }
        when(payments.retrieve(anyString())).thenReturn(intent);
        assertThrows(IllegalArgumentException.class, () -> service.complete(checkoutId, customerId));
        assertEquals(0, orderRepo.count());
        verify(payments, never()).capture(anyString(), any());
    }

    @Test
    void repeatedStartupReusesSnapshotAndPaymentWhileChangedCartsAreRejected() {
        var first = start();
        assertFalse(first.toString().contains("test-secret"));
        var second = start();
        assertEquals(first, second);
        assertEquals(1, checkouts.count());
        verify(payments, times(1)).create(any(), any(), anyLong());
        assertThrows(IllegalArgumentException.class, () -> service.start(customer, checkoutId, restaurantId,
                List.of(new CheckoutService.Item(itemId, 2))));
        assertEquals(1200, checkouts.findById(checkoutId).orElseThrow().getTotalMinor());
    }

    @Test
    void anExistingOrderIdCannotBeReusedToOverwriteItsOrder() {
        var existing = new com.mosbyboys.dishesbecrazy.technical.repository.entity.OrderEntity();
        existing.setId(checkoutId);
        existing.setUserId(customerId);
        existing.setRestaurantId(restaurantId);
        existing.setState("DeliveredState");
        existing.setCreatedAt(Instant.now());
        orderRepo.saveAndFlush(existing);

        assertThrows(IllegalArgumentException.class, this::start);
        assertEquals("DeliveredState", orderRepo.findById(checkoutId).orElseThrow().getState());
        verify(payments, never()).create(any(), any(), anyLong());
    }

    @Test
    void startingAnotherCustomersCheckoutCannotRevealItsClientSecret() {
        start();
        var another = new Customer(UUID.randomUUID(), OffsetDateTime.now(), "Another", "Customer", "other@example.com",
                "other", null, "Montreal", null);
        clearInvocations(payments);
        assertThrows(PermissionDeniedException.class, () -> service.start(another, checkoutId, restaurantId,
                List.of(new CheckoutService.Item(itemId, 1))));
        verifyNoInteractions(payments);
    }

    @Test
    void returningToCartCancelsAuthorizationButNeverDiscardsACapturedPayment() {
        start();
        assertNull(service.cancel(checkoutId, customerId));
        verify(payments).cancel(anyString(), any());
        assertEquals(0, orderRepo.count());
        clearInvocations(payments);
        assertTrue(start().cancelled());
        verifyNoInteractions(payments);
    }

    @Test
    void returningToCartAfterCaptureRecoversAndReturnsTheOrder() {
        start();
        when(payments.retrieve(anyString())).thenReturn(intent("succeeded"));
        assertEquals(checkoutId, service.cancel(checkoutId, customerId).getId());
        assertEquals(1, orderRepo.count());
        verify(payments, never()).cancel(anyString(), any());
        verify(payments, never()).capture(anyString(), any());
    }

    @Test
    void concurrentBrowserAndWebhookCompletionCaptureOnce() throws Exception {
        start();
        var capturing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(payments.capture(anyString(), any())).thenAnswer(call -> {
            capturing.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return intent("succeeded");
        });
        var browser = CompletableFuture.supplyAsync(() -> service.complete(checkoutId, customerId));
        assertTrue(capturing.await(5, TimeUnit.SECONDS));
        var webhook = CompletableFuture.runAsync(() -> service.completeFromWebhook(checkoutId));
        release.countDown();
        assertEquals(checkoutId, browser.get(5, TimeUnit.SECONDS).getId());
        webhook.get(5, TimeUnit.SECONDS);
        assertEquals(1, orderRepo.count());
        assertEquals(1, eventRepo.count());
        assertEquals(1, queues.placed(restaurantId).size());
        verify(payments, times(1)).capture(anyString(), any());
    }

    @Test
    void roundsServerComputedTotalsToCadCents() {
        when(pricing.getTotal(anyList())).thenReturn(breakdown(new BigDecimal("10.00"), new BigDecimal("12.345")));
        start();
        verify(payments).create(checkoutId, customerId, 1235L);
    }

    private CheckoutService.Start start() {
        return service.start(customer, checkoutId, restaurantId, List.of(new CheckoutService.Item(itemId, 1)));
    }

    private PaymentIntent intent(String status) {
        var intent = new PaymentIntent();
        intent.setId("pi_" + checkoutId);
        intent.setClientSecret("test-secret");
        intent.setAmount(1200L);
        intent.setAmountReceived(status.equals("succeeded") ? 1200L : 0L);
        intent.setCurrency("cad");
        intent.setCaptureMethod("manual");
        intent.setStatus(status);
        intent.setMetadata(Map.of("integration", "dishes-checkout-v1", "checkout_id", checkoutId.toString(), "customer_id", customerId.toString()));
        return intent;
    }

    private PricingBreakdown breakdown(BigDecimal subtotal, BigDecimal total) {
        return new PricingBreakdown(subtotal, total.subtract(subtotal), BigDecimal.ZERO, BigDecimal.ZERO,
                total, BigDecimal.ZERO, BigDecimal.ZERO, total);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = CheckoutRepo.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:checkout-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.mosbyboys.dishesbecrazy.technical.repository.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean StripePayments payments() { return mock(StripePayments.class); }
        @Bean MenuService menu() { return mock(MenuService.class); }
        @Bean RestaurantService restaurants() { return mock(RestaurantService.class); }
        @Bean REGFacade users() { return mock(REGFacade.class); }
        @Bean PricingService pricingService() { return mock(PricingService.class); }
        @Bean PRCFacade pricing() { return mock(PRCFacade.class); }
        @Bean OrderQueues queues() { return new OrderQueues(); }
        @Bean OrderService orders(OrderRepo orders, OrderLineItemRepo lines) { return new OrderService(orders, lines); }
        @Bean OrderEventService events(OrderEventRepo events) { return new OrderEventService(events); }
        @Bean ORDFacade facade(OrderService orders, OrderEventService events, OrderQueues queues,
                              MenuService menu, RestaurantService restaurants, PricingService pricing, REGFacade users) {
            return new ORDFacade(orders, events, new PermissionPolicy(), queues, menu, restaurants, pricing, users);
        }
        @Bean CheckoutService checkout(CheckoutRepo repo, StripePayments payments, ORDFacade orders, REGFacade users,
                                       MenuService menu, PRCFacade pricing, PlatformTransactionManager manager) {
            return new CheckoutService(repo, payments, orders, users, menu, pricing, JsonMapper.builder().build(), manager);
        }
    }
}
