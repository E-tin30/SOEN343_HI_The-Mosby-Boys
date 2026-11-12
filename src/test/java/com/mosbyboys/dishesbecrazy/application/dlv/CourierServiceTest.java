package com.mosbyboys.dishesbecrazy.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;
import com.mosbyboys.dishesbecrazy.technical.repository.CourierRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.CustomerRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.OrderRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.CourierEntity;

class CourierServiceTest {

    private final CourierRepo courierRepo = mock(CourierRepo.class);
    private final CourierService service = new CourierService(
            mock(OrderRepo.class), mock(CustomerRepo.class), courierRepo, mock(JdbcTemplate.class));

    @Test
    void returnsTheCouriersOwnState() {
        UUID courierId = UUID.randomUUID();
        CourierEntity courier = new CourierEntity();
        courier.setId(courierId);
        courier.setState(CourierState.ASSIGNED);
        when(courierRepo.findById(courierId)).thenReturn(Optional.of(courier));

        assertEquals(CourierState.ASSIGNED, service.getState(courierId));
    }

    @Test
    void rejectsAnUnknownCourier() {
        UUID courierId = UUID.randomUUID();
        when(courierRepo.findById(courierId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getState(courierId));
    }
}
