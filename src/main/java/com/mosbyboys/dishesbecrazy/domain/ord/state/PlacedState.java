package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Order was placed, waiting on the restaurant. Can be accepted, rejected or cancelled. */
public class PlacedState extends OrderState {
    
    @Override 
    // placed --> accepted
    public OrderState accept() {
        return new AcceptedState();
    }

    @Override 
    // placed --> rejected
    public OrderState reject() {
        return new RejectedState();
    }

    @Override 
    // placed --> cancelled
    public OrderState cancel() {
        return new CancelledState();
    }
}
