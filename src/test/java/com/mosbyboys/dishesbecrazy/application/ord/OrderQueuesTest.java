package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrderQueuesTest {

    @Test
    void sameRestaurantGetsTheSameQueueOfEachType() {
        OrderQueues queues = new OrderQueues();
        UUID restaurantId = UUID.randomUUID();

        assertSame(queues.placed(restaurantId), queues.placed(restaurantId));
        assertSame(queues.preparation(restaurantId), queues.preparation(restaurantId));
        assertSame(queues.ready(restaurantId), queues.ready(restaurantId));
    }

    @Test
    void differentRestaurantsGetDifferentQueues() {
        OrderQueues queues = new OrderQueues();

        assertNotSame(queues.preparation(UUID.randomUUID()), queues.preparation(UUID.randomUUID()));
    }

    @Test
    void anIdInOneQueueIsNotInTheOthers() {
        OrderQueues queues = new OrderQueues();
        UUID restaurantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        queues.placed(restaurantId).enqueue(orderId);

        assertTrue(queues.placed(restaurantId).contains(orderId));
        assertFalse(queues.preparation(restaurantId).contains(orderId));
        assertFalse(queues.ready(restaurantId).contains(orderId));
    }

    @Test
    void nullRestaurantThrows() {
        OrderQueues queues = new OrderQueues();

        assertThrows(IllegalArgumentException.class, () -> queues.placed(null));
        assertThrows(IllegalArgumentException.class, () -> queues.preparation(null));
        assertThrows(IllegalArgumentException.class, () -> queues.ready(null));
    }
}