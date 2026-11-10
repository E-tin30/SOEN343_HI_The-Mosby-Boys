package com.mosbyboys.dishesbecrazy.domain.ord;

/* Subscriber in the Observer pattern: anything that wants to react when an order changes state.
ORDFacade (the publisher) calls update(...) on every subscriber after each transition. */
public interface OrderObserver {

    void update(OrderStateChanged event);
}