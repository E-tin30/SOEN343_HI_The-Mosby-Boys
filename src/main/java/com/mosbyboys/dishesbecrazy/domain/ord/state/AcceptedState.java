package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Restaurant accepted it, it's in the prep queue. Can go to preparing or cancelled. */
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
