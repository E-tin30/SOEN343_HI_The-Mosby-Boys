package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ord.PlacedQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.PreparationQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.ReadyQueue;

/* Holds one placed, one preparation and one ready queue per restaurant,
each created the first time it's asked for, plus the global ready queue that dispatch reads.
Shared by every request, so the maps are ConcurrentHashMaps (R-ORD-03). The global queue is only
reachable through the synchronized methods below, so it is never read and changed at the same time. */
@Component
public class OrderQueues {

    private final Map<UUID, PlacedQueue> placed = new ConcurrentHashMap<>();
    private final Map<UUID, PreparationQueue> preparation = new ConcurrentHashMap<>();
    private final Map<UUID, ReadyQueue> ready = new ConcurrentHashMap<>();
    private final Queue<UUID> globalReady = new ArrayDeque<>();

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

    // helper method to check restaurant id is not null
    private static UUID require(UUID restaurantId) {
        if (restaurantId == null) {
            throw new IllegalArgumentException("Restaurant Id can't be null");
        }
        return restaurantId;
    }

    public synchronized void enqueueGlobalReady(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        
        if (globalReady.contains(orderId)) {
            throw new IllegalArgumentException("Order already queued");
        }
        
        globalReady.add(orderId);
    }

    // A copy of the global ready queue, safe to loop over while others change the queue
    public synchronized List<UUID> globalReadySnapshot() {
        return List.copyOf(globalReady);
    }

    // Takes an order out of the global ready queue. Returns false if it wasn't there
    public synchronized boolean removeGlobalReady(UUID orderId) {
        return globalReady.remove(orderId);
    }
}