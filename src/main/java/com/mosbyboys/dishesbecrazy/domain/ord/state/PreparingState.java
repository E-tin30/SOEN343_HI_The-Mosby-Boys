package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Restaurant is cooking it. Can go to ready (only if first in the queue, R-ORD-03) or be cancelled. */
public class PreparingState extends OrderState {
    
    @Override 
    // preparing --> ready
    public OrderState markReady() {
        return new ReadyState();
    }

    @Override 
    // preparing --> cancelled
    public OrderState cancel() {
        return new CancelledState();
    }

}
