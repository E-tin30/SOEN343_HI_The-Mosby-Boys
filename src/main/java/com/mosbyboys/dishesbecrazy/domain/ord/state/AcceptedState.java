package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Restaurant accepted it, it's in the prep queue. Can only go to preparing.
TODO(team): confirm operator can cancel from accepted (spec: cancel after placed is an operator action). */
public class AcceptedState extends OrderState {
    
   @Override 
   // accepted --> preparing
   public OrderState startPreparing() {
      return new PreparingState();
   }

   @Override 
   // accepted --> cancelled
   public OrderState cancel() {
      return new CancelledState();
   }
}
