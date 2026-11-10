package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.OrderObserver;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/* Concrete subscriber: writes one OrderEvent row for every state change (R-ORD-05).
Runs inside the same transaction as the change, so both are saved together or neither is. */
@Component
@RequiredArgsConstructor
public class OrderEventLogger implements OrderObserver {

    private final OrderEventService orderEventService;

    @Override
    public void update(OrderStateChanged event) {
        orderEventService.save(event.orderId(), event.previousState(), event.newState(),
                event.performedBy(), event.action());
    }
}