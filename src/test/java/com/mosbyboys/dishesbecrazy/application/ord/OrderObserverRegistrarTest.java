package com.mosbyboys.dishesbecrazy.application.ord;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.domain.ord.OrderObserver;

class OrderObserverRegistrarTest {

    @Test
    void subscribesEveryObserverToThePublisher() {
        ORDFacade publisher = mock(ORDFacade.class);
        OrderObserver first = mock(OrderObserver.class);
        OrderObserver second = mock(OrderObserver.class);

        new OrderObserverRegistrar(publisher, List.of(first, second));

        verify(publisher).subscribe(first);
        verify(publisher).subscribe(second);
    }
}