package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Parent of all order states. Every action (accept, reject, startPreparing, markReady, cancel)
throws an exception by default, and each state only overrides the ones that are legal from it (R-ORD-04). */
public class OrderState {
    
}
