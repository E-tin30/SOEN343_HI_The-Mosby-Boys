package com.mosbyboys.dishesbecrazy.application.dlv;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.application.ord.RestaurantService;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;

class DispatchServiceTest {

    private final OrderQueues queues = new OrderQueues();
    private final OrderService orderService = mock(OrderService.class);
    private final CourierService courierService = mock(CourierService.class);
    private final RestaurantService restaurantService = mock(RestaurantService.class);
    private final DispatchService dispatch = new DispatchService(queues, orderService, courierService,
            restaurantService, mock(ApplicationEventPublisher.class), mock(PlatformTransactionManager.class));

    private final UUID restaurantId = UUID.randomUUID();

    private Order stored(String state) {
        Order order = Order.reconstitute(UUID.randomUUID(), OrderStates.fromName(state), UUID.randomUUID(),
                restaurantId, null, List.of(), Instant.now(), null, BigDecimal.ZERO);
        when(orderService.load(order.getId())).thenReturn(order);
        queues.enqueueGlobalReady(order.getId());
        return order;
    }

    @Test
    void ordersNoLongerReadyAreDroppedAndTheRestAreStillOffered() {
        Order cancelled = stored("CancelledState");
        Order ready = stored("ReadyState");
        UUID courierId = UUID.randomUUID();
        when(restaurantService.load(restaurantId)).thenReturn(new Restaurant(restaurantId, OffsetDateTime.now(),
                "A", "B", "a@b.c", "ab", "hash", "Resto", RestaurantStatus.OPEN, new Location(45.5, -73.6)));
        when(courierService.getIdleCouriers())
                .thenReturn(List.of(new CourierData(courierId, CourierState.IDLE, 45.5, -73.6)));

        dispatch.dispatchWaitingOrders();

        assertEquals(List.of(ready.getId()), queues.globalReadySnapshot());
        assertEquals(courierId, ready.getCourierId());
        assertNull(cancelled.getCourierId());
    }
    // A cancelled order at the front used to throw on assignment and stop every order behind it.
}
