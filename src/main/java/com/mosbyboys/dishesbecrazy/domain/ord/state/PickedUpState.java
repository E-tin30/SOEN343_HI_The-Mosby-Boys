package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Order is picked up by courier */
public class PickedUpState extends OrderState {
    @Override
    // pickedUp --> delivered
    public OrderState deliver() { return new DeliveredState(); }

    @Override
    // pickedUp --> cancelled
    public OrderState cancel() {
        return new CancelledState();
    }
}
