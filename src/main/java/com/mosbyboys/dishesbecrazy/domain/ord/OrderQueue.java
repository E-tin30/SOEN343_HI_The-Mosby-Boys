package com.mosbyboys.dishesbecrazy.domain.ord;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import lombok.Getter;

public abstract class OrderQueue {

    @Getter
    private final UUID restaurantId;
    private final Queue<UUID> queue = new ArrayDeque<>();

    protected  OrderQueue(UUID restaurantId){
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
            throw new IllegalStateException("Queue is empty");
        }
        return this.queue.poll();
    }

    // peek at the front of the queue but don't remove
    public synchronized UUID peek(){
        if(this.queue.isEmpty()){
            throw new IllegalStateException("Queue is empty");
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

    // remove this order, but ONLY if it is first in line. Check and removal share one lock,
    // so two simultaneous requests can't both pass the check.
    public synchronized void dequeueIfFirst(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }
        if (!orderId.equals(peek())) {          // peek() throws "Queue is empty" when nothing is waiting
            throw new IllegalStateException("Order " + orderId + " is not next in line");
        }
        dequeue();
    }
}
