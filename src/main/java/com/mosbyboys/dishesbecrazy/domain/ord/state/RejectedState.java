package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Restaurant rejected the order (reason required). Dead end, no more actions.
   TODO(team): is the refund a side effect or its own state? */
public class RejectedState extends OrderState {
   
   // Out of scope
   // @Override 
   //  // rejected --> refunded
   //  public OrderState refund() {
   //      return new RefundedState();
   //  }

}
