package com.mosbyboys.dishesbecrazy.domain.ord;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import lombok.Getter;

/* One restaurant's accepted orders in arrival order (FIFO, R-ORD-03).
Only the order at the front can start preparing. 
Order leaves the queue when order changes from accepted to startPreparing */
public class PreparationQueue {

    @Getter
    private final UUID restaurantId;
    private final Queue<UUID> queue = new ArrayDeque<>();

    public PreparationQueue(UUID restaurantId){
        if(restaurantId == null){
            throw new IllegalArgumentException("Restaurant Id can't be null");
        }

        this.restaurantId = restaurantId;
    }

    // add to the end of the queue
    public synchronized void enqueue(UUID orderId){
        if(orderId == null){
            throw new IllegalArgumentException("Order ID must not be null");
        }
        if (queue.contains(orderId)) {
            throw new IllegalArgumentException("Order " + orderId + " is already in the queue");
        }
        this.queue.add(orderId);
    }

    // remove from the front of the queue and return the order id
    public synchronized UUID dequeue(){
        if(this.queue.isEmpty()){
            throw new IllegalStateException("Queue is empty, nothing to prepare");
        }
        return this.queue.poll();
    }

    // peek at the front of the queue but don't remove
    public synchronized UUID peek(){
        if(this.queue.isEmpty()){
            throw new IllegalStateException("Queue is empty, nothing to prepare");
        }
        return this.queue.peek();
    }

    // remove an order from the queue (if order is cancelled)
    public synchronized boolean remove(UUID orderId){
        if(orderId == null){
            throw new IllegalArgumentException("Order ID must not be null");
        }
        return this.queue.remove(orderId); // returns true if there returns false if not in queue
    }
    
    // returns size of the queue
    public synchronized int size(){
        return queue.size();
    }

    // returns whether or not the queue contains an order
    public synchronized boolean contains(UUID orderId){
        if(orderId == null){
            throw new IllegalArgumentException("Order Id can't be null");
        }
        return queue.contains(orderId);
    }
}
