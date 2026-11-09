package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ord.OrderQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.PlacedQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.PreparationQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.ReadyQueue;

/* Holds one placed, one preparation and one ready queue per restaurant,
each created the first time it's asked for. Shared by every request,
so the maps are ConcurrentHashMaps (R-ORD-03). */
@Component
public class OrderQueues {

    private final Map<UUID, PlacedQueue> placed = new ConcurrentHashMap<>();
    private final Map<UUID, PreparationQueue> preparation = new ConcurrentHashMap<>();
    private final Map<UUID, ReadyQueue> ready = new ConcurrentHashMap<>();

    // place order -> placed queue
    public PlacedQueue placed(UUID restaurantId) {
        return placed.computeIfAbsent(require(restaurantId), PlacedQueue::new);
    }

    // accept order -> preparation queue
    public PreparationQueue preparation(UUID restaurantId) {
        return preparation.computeIfAbsent(require(restaurantId), PreparationQueue::new);
    }

    // finished preparing (ready for pickup) -> ready queue
    public ReadyQueue ready(UUID restaurantId) {
        return ready.computeIfAbsent(require(restaurantId), ReadyQueue::new);
    }

    public void clear() {
        placed.values().forEach(OrderQueue::clear);
        preparation.values().forEach(OrderQueue::clear);
        ready.values().forEach(OrderQueue::clear);
        placed.clear();
        preparation.clear();
        ready.clear();
    }
    // Clears retained queue references as well as registry entries; callers must pause order changes first.

    // helper method to check restaurant id is not null
    private static UUID require(UUID restaurantId) {
        if (restaurantId == null) {
            throw new IllegalArgumentException("Restaurant Id can't be null");
        }
        return restaurantId;
    }
}
