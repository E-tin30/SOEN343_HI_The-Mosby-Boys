package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ord.PreparationQueue;

/* Holds one PreparationQueue per restaurant, created the first time it's asked for.
Shared by every request, so the map is a ConcurrentHashMap (R-ORD-03). */
@Component
public class PreparationQueues {

    private final Map<UUID, PreparationQueue> queues = new ConcurrentHashMap<>();

    public PreparationQueue forRestaurant(UUID restaurantId) {
        if (restaurantId == null) {
            throw new IllegalArgumentException("Restaurant Id can't be null");
        }
        return queues.computeIfAbsent(restaurantId, PreparationQueue::new);
    }
}