package com.mosbyboys.dishesbecrazy.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;

class DLVFacadeTest {

    private final CourierService courierService = mock(CourierService.class);
    private final DispatchService dispatchService = mock(DispatchService.class);
    private final DLVFacade facade = new DLVFacade(
        courierService,
        dispatchService,
        mock(ApplicationEventPublisher.class),
        mock(ORDFacade.class)
    );

    @Test
    void otherUnofferedIdleCouriersExcludeCurrentCourierAndCouriersWithOffers() {
        UUID currentCourierId = UUID.randomUUID();
        UUID offeredCourierId = UUID.randomUUID();
        UUID availableCourierId = UUID.randomUUID();
        CourierData currentCourier = idleCourier(currentCourierId);
        CourierData offeredCourier = idleCourier(offeredCourierId);
        CourierData availableCourier = idleCourier(availableCourierId);

        when(courierService.getIdleCouriers())
            .thenReturn(List.of(currentCourier, offeredCourier, availableCourier));
        when(dispatchService.getPendingOffer(offeredCourierId)).thenReturn(mock(Order.class));

        List<CourierData> result = facade.getOtherUnofferedIdleCouriers(currentCourierId);

        assertEquals(List.of(availableCourier), result);
    }

    private CourierData idleCourier(UUID courierId) {
        return new CourierData(courierId, CourierState.IDLE, 45.5, -73.6);
    }
}
