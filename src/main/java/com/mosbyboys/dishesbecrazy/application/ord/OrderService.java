package com.mosbyboys.dishesbecrazy.application.ord;

/* Runs the ORD use cases (place, accept, reject, prepare, ready, cancel, get).
Every command: check permission, change the order's state, save it, record an event.
No per-request data in fields, Spring only makes one of these. (Singleton) */
public class OrderService {
    
}
