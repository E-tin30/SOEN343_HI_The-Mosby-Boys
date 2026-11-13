package com.mosbyboys.dishesbecrazy.application.dlv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    private CourierEntity stored(CourierState state) {
        CourierEntity courier = new CourierEntity();
        courier.setId(UUID.randomUUID());
        courier.setState(state);
        when(courierRepo.findById(courier.getId())).thenReturn(Optional.of(courier));
        return courier;
    }

    @Test
    void returnsTheCouriersOwnState() {
        CourierEntity courier = stored(CourierState.ASSIGNED);

        assertEquals(CourierState.ASSIGNED, service.getState(courier.getId()));
    }

    @Test
    void rejectsAnUnknownCourier() {
        UUID courierId = UUID.randomUUID();
        when(courierRepo.findById(courierId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getState(courierId));
    }

    @ParameterizedTest
    @EnumSource(value = CourierState.class, names = {"ASSIGNED", "CARRYING"})
    void releasingACourierOnAnOrderMakesThemIdle(CourierState state) {
        CourierEntity courier = stored(state);

        service.release(courier.getId());

        assertEquals(CourierState.IDLE, courier.getState());
        verify(courierRepo).save(courier);
    }

    @ParameterizedTest
    @EnumSource(value = CourierState.class, names = {"IDLE", "OFFLINE"})
    void releasingACourierWhoIsNotOnAnOrderChangesNothing(CourierState state) {
        CourierEntity courier = stored(state);

        service.release(courier.getId());

        assertEquals(state, courier.getState());
        verify(courierRepo, never()).save(any());
    }
}
