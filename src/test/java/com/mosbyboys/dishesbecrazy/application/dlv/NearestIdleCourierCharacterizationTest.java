package com.mosbyboys.dishesbecrazy.application.dlv;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.application.ord.RestaurantService;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;
import com.mosbyboys.dishesbecrazy.domain.ord.state.PickedUpState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.ReadyState;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;

// Characterization tests for DispatchService.findNearestIdleCourier to verify functionality before and after it is refactored.
// Each test sets up waiting orders and idle couriers, then runs dispatchWaitingOrders() which contains the findNearestIdleCourier call.
// The test then checks which courier was offered the order through order.getCourierId() (null means nobody was offered the order).

class NearestIdleCourierCharacterizationTest {
    
    // The restaurant's location: every courier distance in these tests is measured from here
    private static final Location PICKUP = new Location(45.0, -73.0);

    // The real global ready queue that dispatch reads, plus mocked services
    private final OrderQueues queues = new OrderQueues();
    private final OrderService orderService = mock(OrderService.class);
    private final CourierService courierService = mock(CourierService.class);
    private final RestaurantService restaurantService = mock(RestaurantService.class);
    private final DispatchService dispatch = new DispatchService(queues, orderService, courierService,
            restaurantService, mock(ApplicationEventPublisher.class), mock(PlatformTransactionManager.class));

    private final UUID restaurantId = UUID.randomUUID();

    // Stands in for the orders table. Orders are changed in place, so a "saved" order is already up to date here
    private final List<Order> orders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Dispatch loads each order's restaurant to find the pickup point: always PICKUP here
        when(restaurantService.load(restaurantId)).thenReturn(new Restaurant(restaurantId, OffsetDateTime.now(),
                "A", "B", "a@b.c", "ab", "hash", "Resto", RestaurantStatus.OPEN, PICKUP));
        // Dispatch loads each queued order by ID: return it from the in-memory orders table
        when(orderService.load(any())).thenAnswer(call -> {
            UUID orderId = call.getArgument(0);
            return orders.stream().filter(order -> order.getId().equals(orderId)).findFirst().orElseThrow();
        });
        // getPendingOffer calls this to see whether a courier already holds an offer.
        // Same rule as the real query: the courier's orders that are READY or PICKED UP
        when(orderService.findCurrentForCourier(any())).thenAnswer(call -> {
            UUID courierId = call.getArgument(0);
            return orders.stream()
                    .filter(order -> courierId.equals(order.getCourierId()))
                    .filter(order -> order.getState() instanceof ReadyState
                            || order.getState() instanceof PickedUpState)
                    .toList();
        });
    }

    @Test
    void theClosestIdleCourierIsOfferedTheOrderWhereverItIsListed() {
        // One waiting order, and three idle couriers with the closest in the middle of the list
        Order order = waitingOrder();
        idleCouriers(
                courier(id(3), 45.5, -73.0),   // 0.5 north
                courier(id(2), 45.1, -73.0),   // 0.1 north: the closest
                courier(id(1), 46.0, -73.0));  // 1.0 north, smallest ID

        dispatch.dispatchWaitingOrders();

        // Expected: the closest courier gets the order, not the first listed or the one with the smallest ID
        assertEquals(id(2), order.getCourierId());
    }

    @Test
    void distanceIsStraightLineOverLatitudeAndLongitude() {
        // One waiting order, and three couriers whose ranking depends on how distance is measured
        Order order = waitingOrder();
        idleCouriers(
                courier(id(1), 45.0, -70.1),   // 2.9 east only: squared distance 8.41
                courier(id(3), 47.9, -73.0),   // 2.9 north only: squared distance 8.41
                courier(id(2), 47.0, -71.0));  // 2 north and 2 east: squared distance 8.0

        dispatch.dispatchWaitingOrders();

        // Expected: id(2) wins on straight-line distance, but would lose if only one axis or the sum of both were used
        assertEquals(id(2), order.getCourierId());
    }

    @Test
    void aCourierAlreadyHoldingAnOfferIsSkippedEvenWhenClosest() {
        // Courier 1 already holds an offer for another order, and a new order is waiting
        offeredTo(id(1));
        Order order = waitingOrder();
        idleCouriers(
                courier(id(1), 45.0, -73.0),   // at the restaurant, but already holds an offer
                courier(id(2), 46.0, -73.0));  // 1.0 north

        dispatch.dispatchWaitingOrders();

        // Expected: courier 2 gets the new order, even though courier 1 is closer
        assertEquals(id(2), order.getCourierId());
    }

    @Test
    void noCourierIsChosenWhenEveryIdleCourierAlreadyHoldsAnOffer() {
        // The only idle courier already holds an offer, and a new order is waiting
        offeredTo(id(1));
        Order order = waitingOrder();
        idleCouriers(courier(id(1), 45.0, -73.0));

        dispatch.dispatchWaitingOrders();

        // Expected: nobody is offered the new order
        assertNull(order.getCourierId());
    }

    @Test
    void aCourierOfferedOneOrderIsSkippedForTheNextOrderInTheSameDispatch() {
        // Two waiting orders and two idle couriers
        Order first = waitingOrder();
        Order second = waitingOrder();
        idleCouriers(
                courier(id(1), 45.0, -73.0),   // at the restaurant
                courier(id(2), 46.0, -73.0));  // 1.0 north

        dispatch.dispatchWaitingOrders();

        // Expected: courier 1 gets the first order and now holds an offer, so the second goes to courier 2
        assertEquals(id(1), first.getCourierId());
        assertEquals(id(2), second.getCourierId());
    }

    @Test
    void onATieTheSmallerCourierIdWinsWhenListedFirst() {
        // Two couriers exactly the same distance away, with the smaller ID listed first
        Order order = waitingOrder();
        idleCouriers(
                courier(id(1), 46.0, -73.0),   // 1.0 north
                courier(id(2), 44.0, -73.0));  // 1.0 south

        dispatch.dispatchWaitingOrders();

        // Expected: the smaller ID wins the tie
        assertEquals(id(1), order.getCourierId());
    }

    @Test
    void onATieTheSmallerCourierIdWinsWhenListedSecond() {
        // The same tie, but with the smaller ID listed second
        Order order = waitingOrder();
        idleCouriers(
                courier(id(2), 46.0, -73.0),   // 1.0 north
                courier(id(1), 44.0, -73.0));  // 1.0 south

        dispatch.dispatchWaitingOrders();

        // Expected: the smaller ID still wins, so list order does not decide a tie
        assertEquals(id(1), order.getCourierId());
    }

    @Test
    void theTieBreakUsesUuidOrderNotTextOrder() {
        // A tie between two IDs that sort one way as UUIDs and the other way as text.
        // UUID.compareTo compares the two 64-bit halves as signed numbers, so 8000... sorts before 7fff...
        UUID smallerAsUuid = UUID.fromString("80000000-0000-0000-0000-000000000000");
        UUID smallerAsText = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        Order order = waitingOrder();
        idleCouriers(
                courier(smallerAsText, 46.0, -73.0),   // 1.0 north
                courier(smallerAsUuid, 44.0, -73.0));  // 1.0 south

        dispatch.dispatchWaitingOrders();

        // Expected: the ID that is smaller as a UUID wins the tie
        assertEquals(smallerAsUuid, order.getCourierId());
    }

    @Test
    void theFirstEligibleCourierIsTakenEvenWhenItsDistanceIsNotANumber() {
        // A NaN position gives a NaN distance, which never counts as closer than anything.
        // Pins that the only eligible courier is still chosen, whatever its distance.

        // One waiting order, and one idle courier whose position (and so distance) is NaN
        Order order = waitingOrder();
        idleCouriers(courier(id(1), Double.NaN, -73.0));

        dispatch.dispatchWaitingOrders();

        // Expected: the courier is still chosen, because it is the first eligible one
        assertEquals(id(1), order.getCourierId());
    }

    // Small fixed IDs so the tie-break order is easy to read: id(1) < id(2) < id(3)
    private static UUID id(long n) {
        return new UUID(0, n);
    }

    // An idle courier at this position
    private static CourierData courier(UUID id, double latitude, double longitude) {
        return new CourierData(id, CourierState.IDLE, latitude, longitude);
    }

    // The couriers dispatch sees as idle, in this order
    private void idleCouriers(CourierData... couriers) {
        when(courierService.getIdleCouriers()).thenReturn(List.of(couriers));
    }

    // READY, in the global ready queue and not offered to anyone yet
    private Order waitingOrder() {
        return readyAndQueued(null);
    }

    // READY, in the global ready queue and offered to this courier: a pending offer
    private Order offeredTo(UUID courierId) {
        return readyAndQueued(courierId);
    }

    // Builds a READY order at the test restaurant, stores it in the in-memory orders table
    // and adds it to the back of the global ready queue
    private Order readyAndQueued(UUID courierId) {
        Order order = Order.reconstitute(UUID.randomUUID(), OrderStates.fromName("ReadyState"), UUID.randomUUID(),
                restaurantId, courierId, List.of(), Instant.now(), null, BigDecimal.ZERO);
        orders.add(order);
        queues.enqueueGlobalReady(order.getId());
        return order;
    }
}
