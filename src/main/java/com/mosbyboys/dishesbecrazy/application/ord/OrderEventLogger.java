package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/* Observer: writes one OrderEvent row for every state change (R-ORD-05).
Runs inside the same transaction as the change, so both are saved together or neither is. */
@Component
@RequiredArgsConstructor
public class OrderEventLogger {

    private final OrderEventService orderEventService;

    @EventListener
    public void onOrderStateChanged(OrderStateChanged event) {
        orderEventService.save(event.orderId(), event.previousState(), event.newState(),
                event.performedBy(), event.action());
    }
}