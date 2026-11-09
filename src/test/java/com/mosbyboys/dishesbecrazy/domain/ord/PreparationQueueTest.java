package com.mosbyboys.dishesbecrazy.domain.ord;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PreparationQueueTest {

    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void ordersComeOutInTheOrderTheyWentIn() {
        PreparationQueue queue = new PreparationQueue(restaurantId);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        queue.enqueue(a);
        queue.enqueue(b);
        queue.enqueue(c);

        assertEquals(a, queue.dequeue());
        assertEquals(b, queue.dequeue());
        assertEquals(c, queue.dequeue());
    }

    @Test
    void enqueueingTheSameOrderTwiceThrows() {
        PreparationQueue queue = new PreparationQueue(restaurantId);
        UUID a = UUID.randomUUID();
        queue.enqueue(a);

        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(a));
        assertEquals(1, queue.size());
    }

    @Test
    void dequeueOnEmptyQueueThrows() {
        PreparationQueue queue = new PreparationQueue(restaurantId);

        assertThrows(IllegalStateException.class, queue::dequeue);
    }

    @Test
    void removingFromTheMiddleKeepsTheRestInOrder() {
        PreparationQueue queue = new PreparationQueue(restaurantId);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        queue.enqueue(a);
        queue.enqueue(b);
        queue.enqueue(c);

        assertTrue(queue.remove(b));

        assertEquals(a, queue.dequeue());
        assertEquals(c, queue.dequeue());
    }

    @Test
    void removingAnIdThatIsNotThereReturnsFalse() {
        PreparationQueue queue = new PreparationQueue(restaurantId);

        assertFalse(queue.remove(UUID.randomUUID()));
    }

    @Test
    void aThousandOrdersFit() {
        PreparationQueue queue = new PreparationQueue(restaurantId);
        for (int i = 0; i < 1000; i++) {
            queue.enqueue(UUID.randomUUID());
        }

        assertEquals(1000, queue.size());
    }

    @Test
    void nullRestaurantOrOrderIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PreparationQueue(null));
        PreparationQueue queue = new PreparationQueue(restaurantId);
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
    }

    // R-ORD-03: turn rule
    @Test
    void dequeueIfFirstRemovesTheFrontOrder() {
        PreparationQueue queue = new PreparationQueue(restaurantId);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        queue.enqueue(a);
        queue.enqueue(b);

        queue.dequeueIfFirst(a);

        assertEquals(b, queue.peek());
        assertEquals(1, queue.size());
    }

    @Test
    void dequeueIfFirstRejectsAnOrderThatIsNotFirst() {
        PreparationQueue queue = new PreparationQueue(restaurantId);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        queue.enqueue(a);
        queue.enqueue(b);

        assertThrows(IllegalStateException.class, () -> queue.dequeueIfFirst(b));
        assertEquals(a, queue.peek());   // front unchanged
        assertEquals(2, queue.size());   // nothing removed
    }

    @Test
    void dequeueIfFirstOnEmptyOrNullThrows() {
        PreparationQueue queue = new PreparationQueue(restaurantId);

        assertThrows(IllegalStateException.class, () -> queue.dequeueIfFirst(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> queue.dequeueIfFirst(null));
    }

    @Test
    void clearingRemovesPendingOrdersAndTheQueueCanBeReused() {
        PreparationQueue queue = new PreparationQueue(restaurantId);
        UUID previous = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        queue.enqueue(previous);
        queue.enqueue(UUID.randomUUID());

        queue.clear();

        assertEquals(0, queue.size());
        assertFalse(queue.contains(previous));
        assertThrows(IllegalStateException.class, queue::peek);
        queue.enqueue(next);
        assertEquals(next, queue.dequeue());
    }
    // A cleared queue must remain usable for the next restored or newly placed order.

    @Test
    void clearingAnEmptyQueueRepeatedlyIsSafe() {
        PreparationQueue queue = new PreparationQueue(restaurantId);

        queue.clear();
        queue.clear();

        assertEquals(0, queue.size());
    }
    // Repeated Reset operations may encounter queues that are already empty.
}
