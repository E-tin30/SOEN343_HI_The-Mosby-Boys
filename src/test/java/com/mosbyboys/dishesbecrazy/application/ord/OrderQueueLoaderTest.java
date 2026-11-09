package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;

class OrderQueueLoaderTest {

    private final UUID restaurantId = UUID.randomUUID();

    private Order stored(String state) {
        return stored(state, restaurantId, Instant.now());
    }

    private Order stored(String state, UUID restaurant, Instant placedAt) {
        OrderState s = OrderStates.fromName(state);
        return Order.reconstitute(UUID.randomUUID(), s, UUID.randomUUID(), restaurant, null,
                List.of(), placedAt, null, BigDecimal.ZERO);
    }

    @Test
    void ordersStillWaitingGoBackInTheirLineOldestFirst() {
        Order firstPlaced = stored("PlacedState");
        Order secondPlaced = stored("PlacedState");
        Order accepted = stored("AcceptedState");
        Order ready = stored("ReadyState");
        OrderService orderService = mock(OrderService.class);
        when(orderService.findInStates(any())).thenReturn(List.of(firstPlaced, accepted, secondPlaced, ready));
        OrderQueues queues = new OrderQueues();

        new OrderQueueLoader(orderService, queues).run();

        assertEquals(firstPlaced.getId(), queues.placed(restaurantId).peek());
        assertEquals(2, queues.placed(restaurantId).size());
        assertEquals(accepted.getId(), queues.preparation(restaurantId).peek());
        assertEquals(ready.getId(), queues.ready(restaurantId).peek());
    }

    @Test
    void loadingTheSameOrdersAgainReplacesQueuesWithoutDuplicates() {
        Order first = stored("AcceptedState");
        Order second = stored("AcceptedState");
        OrderService orderService = mock(OrderService.class);
        when(orderService.findInStates(any())).thenReturn(List.of(first, second));
        OrderQueues queues = new OrderQueues();
        var loader = new OrderQueueLoader(orderService, queues);
        loader.run();

        loader.run();

        assertEquals(2, queues.preparation(restaurantId).size());
        assertEquals(first.getId(), queues.preparation(restaurantId).dequeue());
        assertEquals(second.getId(), queues.preparation(restaurantId).dequeue());
    }
    // Reset must be repeatable while preserving the persisted FIFO order.

    @Test
    void loadingAnEmptyDatasetRemovesStaleOrders() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.findInStates(any())).thenReturn(List.of());
        OrderQueues queues = new OrderQueues();
        queues.placed(restaurantId).enqueue(UUID.randomUUID());
        queues.preparation(restaurantId).enqueue(UUID.randomUUID());
        queues.ready(restaurantId).enqueue(UUID.randomUUID());

        new OrderQueueLoader(orderService, queues).run();

        assertEquals(0, queues.placed(restaurantId).size());
        assertEquals(0, queues.preparation(restaurantId).size());
        assertEquals(0, queues.ready(restaurantId).size());
    }
    // Orders absent from persistence must not survive in memory after rebuilding.

    @Test
    void rebuildingMovesAnOrderToItsLatestPersistedState() {
        Order order = stored("PlacedState");
        OrderService orderService = mock(OrderService.class);
        when(orderService.findInStates(any())).thenReturn(List.of(order));
        OrderQueues queues = new OrderQueues();
        var loader = new OrderQueueLoader(orderService, queues);
        loader.run();
        var previousPlacedQueue = queues.placed(restaurantId);
        order.accept();
        order.startPreparing();
        order.markReady();

        loader.rebuild();

        assertEquals(0, previousPlacedQueue.size());
        assertEquals(0, queues.placed(restaurantId).size());
        assertEquals(0, queues.preparation(restaurantId).size());
        assertEquals(order.getId(), queues.ready(restaurantId).dequeue());
        assertEquals(0, queues.ready(restaurantId).size());
    }
    // Restoring a changed order must not leave its ID in an obsolete queue.

    @Test
    void rebuildingDropsOrdersForARestaurantMissingFromTheNewDataset() {
        Order removed = stored("AcceptedState");
        UUID remainingRestaurant = UUID.randomUUID();
        Order remaining = stored("AcceptedState", remainingRestaurant, Instant.now());
        OrderService orderService = mock(OrderService.class);
        when(orderService.findInStates(any())).thenReturn(List.of(removed), List.of(remaining));
        OrderQueues queues = new OrderQueues();
        var loader = new OrderQueueLoader(orderService, queues);
        loader.run();
        var previousQueue = queues.preparation(restaurantId);

        loader.rebuild();

        assertEquals(0, previousQueue.size());
        assertEquals(0, queues.preparation(restaurantId).size());
        assertEquals(1, queues.preparation(remainingRestaurant).size());
        assertEquals(remaining.getId(), queues.preparation(remainingRestaurant).peek());
    }
    // Queue contents come entirely from the new snapshot, including its restaurant membership.

    @Test
    void aFailedReadLeavesAllExistingQueueContentsIntact() {
        OrderService orderService = mock(OrderService.class);
        var failure = new IllegalStateException("Persistence unavailable");
        when(orderService.findInStates(any())).thenThrow(failure);
        OrderQueues queues = new OrderQueues();
        UUID placed = UUID.randomUUID();
        UUID accepted = UUID.randomUUID();
        UUID ready = UUID.randomUUID();
        queues.placed(restaurantId).enqueue(placed);
        queues.preparation(restaurantId).enqueue(accepted);
        queues.ready(restaurantId).enqueue(ready);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> new OrderQueueLoader(orderService, queues).rebuild()));

        assertEquals(1, queues.placed(restaurantId).size());
        assertEquals(placed, queues.placed(restaurantId).peek());
        assertEquals(1, queues.preparation(restaurantId).size());
        assertEquals(accepted, queues.preparation(restaurantId).peek());
        assertEquals(1, queues.ready(restaurantId).size());
        assertEquals(ready, queues.ready(restaurantId).peek());
    }
    // A database failure must not erase the last usable in-memory queue state.

    @Test
    void rebuildingRestoresTheZeroThreeSixPreparationBaselineAndIgnoresOtherStates() {
        UUID emptyRestaurant = restaurantId;
        UUID buildingRestaurant = UUID.randomUUID();
        UUID busyRestaurant = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-01T12:00:00Z");
        List<Order> orders = new ArrayList<>();
        List<UUID> buildingIds = new ArrayList<>();
        List<UUID> busyIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (i < 3) {
                Order accepted = stored("AcceptedState", buildingRestaurant, start.plusSeconds(i * 2));
                orders.add(accepted);
                buildingIds.add(accepted.getId());
            }
            Order accepted = stored("AcceptedState", busyRestaurant, start.plusSeconds(i * 2 + 1));
            orders.add(accepted);
            busyIds.add(accepted.getId());
        }
        for (String state : List.of("PreparingState", "PickedUpState", "DeliveredState",
                "RefundedState", "RejectedState", "CancelledState")) {
            orders.add(stored(state, emptyRestaurant, start.plusSeconds(60)));
        }
        OrderService orderService = mock(OrderService.class);
        when(orderService.findInStates(any())).thenReturn(orders);
        OrderQueues queues = new OrderQueues();
        var loader = new OrderQueueLoader(orderService, queues);

        loader.rebuild();
        loader.rebuild();

        assertEquals(0, queues.preparation(emptyRestaurant).size());
        assertEquals(0, queues.placed(emptyRestaurant).size());
        assertEquals(0, queues.ready(emptyRestaurant).size());
        assertEquals(3, queues.preparation(buildingRestaurant).size());
        assertEquals(6, queues.preparation(busyRestaurant).size());
        for (UUID id : buildingIds) {
            assertEquals(id, queues.preparation(buildingRestaurant).dequeue());
        }
        for (UUID id : busyIds) {
            assertEquals(id, queues.preparation(busyRestaurant).dequeue());
        }
        verify(orderService, times(2)).findInStates(List.of("PlacedState", "AcceptedState", "ReadyState"));
    }
    // Persisted FIFO order restores the three map bands; cooking/carried/terminal states stay out of the queues.
}
