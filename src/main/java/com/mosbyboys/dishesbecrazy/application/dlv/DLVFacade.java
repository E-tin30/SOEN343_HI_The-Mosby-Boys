package com.mosbyboys.dishesbecrazy.application.dlv;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.application.dlv.DispatchService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DLVFacade {
    private final CourierService courierService;
    private final DispatchService dispatchService;

    public List<CourierData> getOnlineCouriers() {
        return courierService.getOnlineCouriers();
    }

    public Order getPendingOffer(UUID courierId) {
        return dispatchService.getPendingOffer(courierId);
    }
}

