package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.OrderObserver;
import org.springframework.stereotype.Component;

import java.util.List;

/* Client in the Observer pattern: at startup it takes the publisher (ORDFacade) and every
subscriber (each OrderObserver bean) and subscribes them, so nothing else has to know the others exist. */
@Component
public class OrderObserverRegistrar {

    public OrderObserverRegistrar(ORDFacade publisher, List<OrderObserver> subscribers) {
        subscribers.forEach(publisher::subscribe);
    }
}