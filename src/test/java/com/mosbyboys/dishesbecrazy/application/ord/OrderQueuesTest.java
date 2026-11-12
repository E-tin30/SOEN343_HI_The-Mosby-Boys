package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
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

    @Test
    void clearingEmptiesEveryRestaurantAndRetainedQueueReference() {
        OrderQueues queues = new OrderQueues();
        UUID firstRestaurant = UUID.randomUUID();
        UUID removedRestaurant = UUID.randomUUID();
        var previousQueues = List.of(queues.placed(firstRestaurant), queues.preparation(firstRestaurant),
                queues.ready(firstRestaurant), queues.placed(removedRestaurant),
                queues.preparation(removedRestaurant), queues.ready(removedRestaurant));
        previousQueues.forEach(queue -> queue.enqueue(UUID.randomUUID()));

        queues.clear();
        queues.clear();

        previousQueues.forEach(queue -> assertEquals(0, queue.size()));
        for (UUID restaurant : List.of(firstRestaurant, removedRestaurant)) {
            assertEquals(0, queues.placed(restaurant).size());
            assertEquals(0, queues.preparation(restaurant).size());
            assertEquals(0, queues.ready(restaurant).size());
        }
    }
    // Dropping registry entries alone would leave stale IDs in previously obtained queue objects.

    @Test
    void replacingTheGlobalReadyQueueDropsStaleIdsAndKeepsTheNewOrder() {
        OrderQueues queues = new OrderQueues();
        UUID stale = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        queues.enqueueGlobalReady(stale);

        queues.replaceGlobalReady(List.of(first, second));

        assertEquals(List.of(first, second), queues.globalReadySnapshot());
    }
}
