package com.mosbyboys.dishesbecrazy.application.dlv;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import com.mosbyboys.dishesbecrazy.application.events.DispatchRequestEvent;
import com.mosbyboys.dishesbecrazy.application.dlv.DispatchService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DLVFacade {
    private final CourierService courierService;
    private final DispatchService dispatchService;
    private final ApplicationEventPublisher eventPublisher;
    private final ORDFacade ordFacade;

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

    public void goOffline(UUID courierId) {
        dispatchService.goOffline(courierId);
    }

    //Order goes READY -> PICKED_UP and the courier ASSIGNED -> CARRYING at the same time (thread-safe)
    @Transactional
    public void confirmPickup(UUID courierId, UUID orderId) {
        //Checks the courier is ASSIGNED to this order, so it must run before they become CARRYING
        ordFacade.pickUpOrder(courierId, orderId);

        courierService.markCarrying(courierId);
    }

    //Order goes PICKED_UP -> DELIVERED and the courier CARRYING -> IDLE at the same time (thread-safe)
    @Transactional
    public void confirmDelivery(UUID courierId, UUID orderId) {
        //Checks the courier is CARRYING this order, so it must run before they become IDLE
        ordFacade.deliverOrder(courierId, orderId);

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

}

