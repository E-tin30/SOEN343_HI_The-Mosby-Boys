package com.mosbyboys.dishesbecrazy.domain.ord.state;

public class IllegalTransitionException extends RuntimeException {
    
    // constructor that builds the message from the current state and the attempted action
    // Ex: "Can't markReady from PlacedState"
    public IllegalTransitionException(OrderState currentState, String action){
        super("Can't " + action + " from " + currentState.getClass().getSimpleName());
    }

}
