package com.mosbyboys.dishesbecrazy.application.dlv;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.mosbyboys.dishesbecrazy.application.events.DispatchRequestEvent;
import com.mosbyboys.dishesbecrazy.application.dlv.DispatchService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DLVFacade {
    private final CourierService courierService;
    private final DispatchService dispatchService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderService orderService;

    public List<CourierData> getOnlineCouriers() {
        return courierService.getOnlineCouriers();
    }

    public Order getPendingOffer(UUID courierId) {
        return dispatchService.getPendingOffer(courierId);
    }

    @Transactional
    public void goOnDuty(UUID courierId) {
        courierService.markIdle(courierId);

        eventPublisher.publishEvent(new DispatchRequestEvent());
    }

    @Transactional
    public void goOffline(UUID courierId) {
        Order pendingOffer = getPendingOffer(courierId);

        //Reject any outstanding offer
        if (pendingOffer != null) {
            pendingOffer.clearCourierOffer(courierId);
            orderService.save(pendingOffer);
        }

        //Change courier state to OFFLINE
        courierService.markOffline(courierId);

        //Retry dispatch if an offer was rejected
        if (pendingOffer != null) {
            eventPublisher.publishEvent(new DispatchRequestEvent());
        }
    }

    @Transactional
    public void confirmDelivery(UUID courierId, UUID orderId) {
        //Courier's location updates to where they just dropped the meal off
        courierService.moveToDropoffLocation(courierId, orderId);

        //Courier becomes available again
        courierService.markIdle(courierId);

        // Check for waiting orders
        eventPublisher.publishEvent(
            new DispatchRequestEvent()
        );
    }

    @Transactional
    public void acceptOffer(UUID courierId, UUID orderId) {
        dispatchService.acceptOffer(courierId, orderId);
    }

    @Transactional
    public void carryDelivery(UUID courierId) {
        courierService.markCarrying(courierId);
    }

}

