package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PreparationQueuesTest {

    @Test
    void sameRestaurantGetsTheSameQueue() {
        PreparationQueues queues = new PreparationQueues();
        UUID restaurantId = UUID.randomUUID();

        assertSame(queues.forRestaurant(restaurantId), queues.forRestaurant(restaurantId));
    }

    @Test
    void differentRestaurantsGetDifferentQueues() {
        PreparationQueues queues = new PreparationQueues();

        assertNotSame(queues.forRestaurant(UUID.randomUUID()), queues.forRestaurant(UUID.randomUUID()));
    }

    @Test
    void nullRestaurantThrows() {
        PreparationQueues queues = new PreparationQueues();

        assertThrows(IllegalArgumentException.class, () -> queues.forRestaurant(null));
    }
}