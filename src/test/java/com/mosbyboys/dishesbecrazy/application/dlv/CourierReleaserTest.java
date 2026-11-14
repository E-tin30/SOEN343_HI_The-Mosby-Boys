package com.mosbyboys.dishesbecrazy.application.dlv;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;

import com.mosbyboys.dishesbecrazy.application.dlv.events.DispatchRequestEvent;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;

class CourierReleaserTest {

    private final OrderService orderService = mock(OrderService.class);
    private final CourierService courierService = mock(CourierService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final CourierReleaser releaser = new CourierReleaser(orderService, courierService, eventPublisher);

    private final UUID orderId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();

    private void orderHasCourier(UUID courier) {
        when(orderService.load(orderId)).thenReturn(Order.reconstitute(orderId, OrderStates.fromName("CancelledState"),
                UUID.randomUUID(), UUID.randomUUID(), courier, List.of(), Instant.now(), null, BigDecimal.ZERO));
    }

    private OrderStateChanged cancelled(String from) {
        return new OrderStateChanged(orderId, from, "CancelledState", UUID.randomUUID(), "CANCEL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ReadyState", "PickedUpState"})
    void cancellingAnOrderACourierIsOnReleasesThemAndRunsDispatch(String from) {
        orderHasCourier(courierId);

        releaser.update(cancelled(from));

        verify(courierService).release(courierId);
        verify(eventPublisher).publishEvent(any(DispatchRequestEvent.class));
    }

    @Test
    void cancellingAPastDeliveryLeavesItsCourierAlone() {
        orderHasCourier(courierId);

        releaser.update(cancelled("DeliveredState"));

        verifyNoInteractions(courierService, eventPublisher);
    }
    // By then the courier may be on another order, and releasing them would cut that delivery short.

    @Test
    void ordersWithoutACourierAndOtherChangesAreIgnored() {
        orderHasCourier(null);

        releaser.update(cancelled("ReadyState"));
        releaser.update(new OrderStateChanged(orderId, "PreparingState", "ReadyState", UUID.randomUUID(), "READY"));

        verifyNoInteractions(courierService, eventPublisher);
    }
}
