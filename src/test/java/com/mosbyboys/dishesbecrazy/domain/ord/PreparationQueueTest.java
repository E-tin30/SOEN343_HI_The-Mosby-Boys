package com.mosbyboys.dishesbecrazy.domain.ord;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PreparationQueueTest {

    // helper: a fresh, empty queue
    private PreparationQueue newQueue() {
        return new PreparationQueue(UUID.randomUUID());
    }

    // orders come out in the order they went in (FIFO, R-ORD-03)
    @Test
    void ordersComeOutInArrivalOrder() {
        PreparationQueue queue = newQueue();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        queue.enqueue(a);
        queue.enqueue(b);
        queue.enqueue(c);

        assertEquals(a, queue.dequeue());
        assertEquals(b, queue.dequeue());
        assertEquals(c, queue.dequeue());
    }

    // same id twice throws IllegalArgumentException
    @Test
    void sameIdThrowsIllegalArgumentException() {
        PreparationQueue queue = newQueue();
        UUID a = UUID.randomUUID();

        queue.enqueue(a);

        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(a));
        assertEquals(1, queue.size());
    }

    // dequeue on an empty queue throws IllegalStateException
    @Test
    void dequeueOnEmptyQueueThrowsIllegalStateException() {
        PreparationQueue queue = newQueue();

        assertThrows(IllegalStateException.class, () -> queue.dequeue());
    }

    // remove the middle id: the rest keep their order; removing a missing id returns false
    @Test
    void removeMiddleIdKeepsOrder() {
        PreparationQueue queue = newQueue();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        queue.enqueue(a);
        queue.enqueue(b);
        queue.enqueue(c);

        assertTrue(queue.remove(b));
        assertEquals(a, queue.dequeue());
        assertEquals(c, queue.dequeue());
    }

    // removing a missing id returns false
    @Test
    void removingMissingIdReturnsFalse() {
        PreparationQueue queue = newQueue();

       assertFalse(queue.remove(UUID.randomUUID()));
    }

    // 1000 orders fit, size() is 1000
    @Test
    void queueHasNoCapacity() {
        PreparationQueue queue = newQueue();

        for(int i=0; i < 1000; i++){
            queue.enqueue(UUID.randomUUID());
        }

       assertEquals(1000, queue.size());
    }
}