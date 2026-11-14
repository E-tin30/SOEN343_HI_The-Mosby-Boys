package com.mosbyboys.dishesbecrazy.application.dlv;

import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.application.dlv.events.DispatchRequestEvent;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderObserver;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;

import lombok.RequiredArgsConstructor;

// Free a courier if therie order is cancelled and set them to idle
@Component
@RequiredArgsConstructor
public class CourierReleaser implements OrderObserver {

    private static final Set<String> COURIER_ON_ORDER = Set.of("ReadyState", "PickedUpState");

    private final OrderService orderService;
    private final CourierService courierService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void update(OrderStateChanged event) {
        if (!"CancelledState".equals(event.newState()) || !COURIER_ON_ORDER.contains(event.previousState())) {
            return;
        }
        UUID courierId = orderService.load(event.orderId()).getCourierId();
        if (courierId == null) {
            return;
        }
        courierService.release(courierId);
        eventPublisher.publishEvent(new DispatchRequestEvent());
    }
}
