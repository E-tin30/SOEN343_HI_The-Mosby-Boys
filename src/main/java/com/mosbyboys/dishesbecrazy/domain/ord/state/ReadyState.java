package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Food is ready for pickup. */
public class ReadyState extends OrderState {
    
     @Override
     //ready --> pickedUp
     public OrderState pickUp() { return new PickedUpState(); }

    @Override 
    // ready --> cancelled
    public OrderState cancel() {
        return new CancelledState();
    }
}
