package com.mosbyboys.dishesbecrazy.application.dlv;

import com.mosbyboys.dishesbecrazy.application.dlv.events.OrderReadyEvent;
import com.mosbyboys.dishesbecrazy.application.dlv.events.DispatchRequestEvent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DispatchEventListener {

    private final DispatchService dispatchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderReady(OrderReadyEvent event) {
        dispatchService.dispatchWaitingOrders();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDispatchRequested(DispatchRequestEvent event) {
        dispatchService.dispatchWaitingOrders();
    }
    
}