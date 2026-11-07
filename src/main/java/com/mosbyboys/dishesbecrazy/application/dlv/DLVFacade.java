package com.mosbyboys.dishesbecrazy.application.dlv;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DLVFacade {
    private final CourierService courierService;

    public List<CourierData> getOnlineCouriers() {
        return courierService.getOnlineCouriers();
    }
}

