package com.mosbyboys.dishesbecrazy.application.dlv;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.application.ord.RestaurantService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;
import com.mosbyboys.dishesbecrazy.domain.ord.state.PickedUpState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.ReadyState;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;

// Characterization tests for DispatchService.reassignCourier to verify functionality before and after it is refactored.
// Each test sets up the orders OLD and NEW hold, then calls reassignCourier(OLD, NEW). OLD is ASSIGNED and NEW
// is IDLE unless the test changes them. Tests that expect a refusal use assertRefusedWithoutChanges, which makes
// the call and checks that nothing changed.

class ReassignCourierCharacterizationTest {

    // OLD gives the order up and NEW takes it over
    private static final UUID OLD = new UUID(0, 1);
    private static final UUID NEW = new UUID(0, 2);

    // The real global ready queue, plus mocks. The transaction manager is a mock so the tests can see
    // when a transaction starts, commits or rolls back
    private final OrderQueues queues = new OrderQueues();
    private final OrderService orderService = mock(OrderService.class);
    private final CourierService courierService = mock(CourierService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final DispatchService dispatch = new DispatchService(queues, orderService, courierService,
            mock(RestaurantService.class), mock(ApplicationEventPublisher.class), transactionManager);

    // Stands in for the orders table. Orders are changed in place, so a "saved" order is already up to date here
    private final List<Order> orders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // reassignCourier calls this to find OLD's order, and getPendingOffer calls it to check NEW's offers.
        // Same rule as the real query: the courier's orders that are READY or PICKED UP
        when(orderService.findCurrentForCourier(any())).thenAnswer(call -> {
            UUID courierId = call.getArgument(0);
            return orders.stream()
                    .filter(order -> courierId.equals(order.getCourierId()))
                    .filter(order -> order.getState() instanceof ReadyState
                            || order.getState() instanceof PickedUpState)
                    .toList();
        });
        // A valid reassignment unless a test says otherwise
        when(courierService.getState(OLD)).thenReturn(CourierState.ASSIGNED);
        when(courierService.getState(NEW)).thenReturn(CourierState.IDLE);
    }

    @Test
    void theOldCouriersReadyOrderBecomesAnOfferForTheNewCourier() {
        // OLD has accepted an order
        Order order = acceptedBy(OLD);

        dispatch.reassignCourier(OLD, NEW);

        // Expected: NEW holds the order, the order is saved, and OLD is released
        assertEquals(NEW, order.getCourierId());
        verify(orderService).save(order);
        verify(courierService).release(OLD);

        // The order is back in the ready queue with NEW on it, which is what a pending offer is
        assertEquals(List.of(order.getId()), queues.globalReadySnapshot());
        assertSame(order, dispatch.getPendingOffer(NEW));

        // NEW stays IDLE until they accept the offer
        verify(courierService, never()).markAssigned(any());
    }

    @Test
    void savesThenReleasesInOneNewTransactionAndRequeuesOnlyAfterItCommits() {
        // OLD has accepted an order
        Order order = acceptedBy(OLD);

        // Records, at commit time, whether the order is already back in the queue
        List<Boolean> queuedAtCommit = new ArrayList<>();
        doAnswer(call -> queuedAtCommit.add(queues.globalReadySnapshot().contains(order.getId())))
                .when(transactionManager).commit(any());

        dispatch.reassignCourier(OLD, NEW);

        // Expected, in this order: a new transaction starts, the order is saved, OLD is released,
        // and the transaction commits
        InOrder inOrder = inOrder(transactionManager, orderService, courierService);
        inOrder.verify(transactionManager).getTransaction(argThat((TransactionDefinition definition) ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        inOrder.verify(orderService).save(order);
        inOrder.verify(courierService).release(OLD);
        inOrder.verify(transactionManager).commit(any());
        
        // Exactly one commit, and the order was not back in the queue yet when it happened
        assertEquals(List.of(false), queuedAtCommit);
    }

    @Test
    void anOrderStillInTheReadyQueueIsNotQueuedAgainAndKeepsItsPlace() {
        // OLD's order is still in the ready queue, with another order waiting behind it.
        // Not expected in normal use (accepting an offer takes the order out of the queue),
        // but the method checks for it, so the check is pinned
        Order order = offeredTo(OLD);
        Order waiting = waitingOrder();

        dispatch.reassignCourier(OLD, NEW);

        // Expected: NEW holds the order, and the queue is unchanged: no duplicate, and the order kept its place
        assertEquals(NEW, order.getCourierId());
        assertEquals(List.of(order.getId(), waiting.getId()), queues.globalReadySnapshot());
    }

    @ParameterizedTest
    @EnumSource(value = CourierState.class, names = "ASSIGNED", mode = EnumSource.Mode.EXCLUDE)
    void theOldCourierMustBeAssigned(CourierState state) {
        // OLD has an order but is not ASSIGNED (runs once each for OFFLINE, IDLE and CARRYING)
        Order order = acceptedBy(OLD);
        when(courierService.getState(OLD)).thenReturn(state);

        // Expected: the reassignment is refused, nothing changes, and OLD keeps the order
        assertRefusedWithoutChanges("Old courier must be ASSIGNED");
        assertEquals(OLD, order.getCourierId());
    }

    @ParameterizedTest
    @EnumSource(value = CourierState.class, names = "IDLE", mode = EnumSource.Mode.EXCLUDE)
    void theNewCourierMustBeIdle(CourierState state) {
        // OLD has an order, but NEW is not IDLE (runs once each for OFFLINE, ASSIGNED and CARRYING)
        Order order = acceptedBy(OLD);
        when(courierService.getState(NEW)).thenReturn(state);

        // Expected: the reassignment is refused, nothing changes, and OLD keeps the order
        assertRefusedWithoutChanges("New courier must be IDLE");
        assertEquals(OLD, order.getCourierId());
    }

    @Test
    void theNewCourierMustNotAlreadyHoldAnOffer() {
        // OLD has an order, but NEW already holds an offer for another order
        Order order = acceptedBy(OLD);
        Order offer = offeredTo(NEW);

        // Expected: the reassignment is refused, and both orders keep their couriers
        assertRefusedWithoutChanges("New courier already has a pending offer");
        assertEquals(OLD, order.getCourierId());
        assertEquals(NEW, offer.getCourierId());
    }

    @Test
    void theOldCourierMustHaveAReadyOrderAndAPickedUpOneDoesNotCount() {
        // OLD's only order is already picked up, so there is no READY order to move
        Order pickedUp = pickedUpBy(OLD);

        // Expected: the reassignment is refused, and the picked-up order stays with OLD
        assertRefusedWithoutChanges("No ready order found for the old courier");
        assertEquals(OLD, pickedUp.getCourierId());
    }

    // The next three tests pin the order of the checks: each makes several checks fail at once
    // and expects the message of the one that runs first

    @Test
    void whenEveryCheckWouldFailTheOldCourierIsReportedFirst() {
        // Every check would fail: OLD is not ASSIGNED, NEW is not IDLE, NEW holds an offer,
        // and OLD has no READY order
        offeredTo(NEW);
        when(courierService.getState(OLD)).thenReturn(CourierState.IDLE);
        when(courierService.getState(NEW)).thenReturn(CourierState.ASSIGNED);

        // Expected: OLD's state is checked first, so its message is the one reported
        assertRefusedWithoutChanges("Old courier must be ASSIGNED");
    }

    @Test
    void theNewCouriersStateIsCheckedBeforeItsOffers() {
        // OLD is fine, but NEW is not IDLE and holds an offer, and OLD has no READY order
        offeredTo(NEW);
        when(courierService.getState(NEW)).thenReturn(CourierState.CARRYING);

        // Expected: NEW's state is reported, before its offer and the missing order
        assertRefusedWithoutChanges("New courier must be IDLE");
    }

    @Test
    void bothCouriersAreCheckedBeforeTheOrderIsLookedUp() {
        // Both couriers are in the right state, but NEW holds an offer and OLD has no READY order
        offeredTo(NEW);

        // Expected: NEW's offer is reported, so the couriers are checked before OLD's order is looked up
        assertRefusedWithoutChanges("New courier already has a pending offer");
    }

    // Runs reassignCourier(OLD, NEW) and checks it is refused with this message and leaves no trace:
    // nothing saved or released, the ready queue as it was, and the transaction rolled back instead of committed
    private void assertRefusedWithoutChanges(String message) {
        List<UUID> queueBefore = queues.globalReadySnapshot();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> dispatch.reassignCourier(OLD, NEW));

        assertEquals(message, error.getMessage());
        assertEquals(queueBefore, queues.globalReadySnapshot());
        verify(orderService, never()).save(any());
        verify(courierService, never()).release(any());
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    // READY with this courier on it and out of the ready queue: an accepted offer
    private Order acceptedBy(UUID courierId) {
        return order("ReadyState", courierId, false);
    }

    // READY with this courier on it and still in the ready queue: a pending offer
    private Order offeredTo(UUID courierId) {
        return order("ReadyState", courierId, true);
    }

    // READY, in the ready queue and not offered to anyone yet
    private Order waitingOrder() {
        return order("ReadyState", null, true);
    }

    // PICKED UP by this courier: already on its way, so out of the ready queue
    private Order pickedUpBy(UUID courierId) {
        return order("PickedUpState", courierId, false);
    }

    // Builds an order in this state, stores it in the in-memory orders table
    // and, if queued, adds it to the back of the ready queue
    private Order order(String state, UUID courierId, boolean queued) {
        Order order = Order.reconstitute(UUID.randomUUID(), OrderStates.fromName(state), UUID.randomUUID(),
                UUID.randomUUID(), courierId, List.of(), Instant.now(), null, BigDecimal.ZERO);
        orders.add(order);
        if (queued) {
            queues.enqueueGlobalReady(order.getId());
        }
        return order;
    }
}
