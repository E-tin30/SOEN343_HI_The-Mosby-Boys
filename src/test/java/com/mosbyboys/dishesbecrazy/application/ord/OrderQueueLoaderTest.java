package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;

class OrderQueueLoaderTest {

    private final UUID restaurantId = UUID.randomUUID();

    private Order stored(String state) {
        OrderState s = OrderStates.fromName(state);
        return Order.reconstitute(UUID.randomUUID(), s, UUID.randomUUID(), restaurantId,
                List.of(), Instant.now(), null);
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
}
