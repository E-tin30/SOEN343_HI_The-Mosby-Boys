package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Order has been delivered to the customer by courier */
public class DeliveredState extends OrderState {
    @Override
    // delivered --> cancelled
    public OrderState cancel() {
        return new CancelledState();
    }
}
