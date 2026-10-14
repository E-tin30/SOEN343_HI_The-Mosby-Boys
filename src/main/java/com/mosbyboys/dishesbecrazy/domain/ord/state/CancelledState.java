package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Order was cancelled (by the customer while placed, or by an operator later). 
Dead end, no more actions. */
public class CancelledState extends OrderState {
    
    // Out of scope
    // @Override 
    // cancelled --> refunded
    // public OrderState refund() {
    //     return new RefundedState();
    // }
   
}
