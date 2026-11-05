package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Order was canceled (by the customer while placed, or by an operator later).
Dead end, no more actions. */
public class CancelledState extends OrderState {

    @Override
    //cancelled --> refunded
    public OrderState refund() {
        return new RefundedState();
    }
   
}
