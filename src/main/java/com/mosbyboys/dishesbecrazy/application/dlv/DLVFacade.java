package com.mosbyboys.dishesbecrazy.application.dlv;

import com.mosbyboys.dishesbecrazy.domain.reg.Courier;

public class DLVFacade {
    
    public List<Courier> getOnDutyCouriers() {
        return courierService.getOnDutyCouriers();
    }
}
