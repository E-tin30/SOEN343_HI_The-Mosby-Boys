package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Order has been delivered to the customer by courier */
public class DeliveredState extends OrderState {
    @Override
    // delivered --> cancelled
    public OrderState cancel() {
        return new CancelledState();
    }

    @Override
    public OrderState refund() {
        return new RefundedState();
    }
    // Refunding a completed delivery ends its lifecycle instead of returning it to pickup readiness.
}
