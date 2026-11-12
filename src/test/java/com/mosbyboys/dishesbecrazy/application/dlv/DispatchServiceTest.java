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
        return stored(state, restaurantId);
    }

    private Order stored(String state, UUID restaurant) {
        Order order = Order.reconstitute(UUID.randomUUID(), OrderStates.fromName(state), UUID.randomUUID(),
                restaurant, null, List.of(), Instant.now(), null, BigDecimal.ZERO);
        when(orderService.load(order.getId())).thenReturn(order);
        queues.enqueueGlobalReady(order.getId());
        return order;
    }

    private void restaurantAt(UUID id, Location location) {
        when(restaurantService.load(id)).thenReturn(new Restaurant(id, OffsetDateTime.now(),
                "A", "B", "a@b.c", "ab", "hash", "Resto", RestaurantStatus.OPEN, location));
    }

    private UUID idleCourier() {
        UUID courierId = UUID.randomUUID();
        when(courierService.getIdleCouriers())
                .thenReturn(List.of(new CourierData(courierId, CourierState.IDLE, 45.5, -73.6)));
        return courierId;
    }

    @Test
    void ordersNoLongerReadyAreDroppedAndTheRestAreStillOffered() {
        Order cancelled = stored("CancelledState");
        Order ready = stored("ReadyState");
        restaurantAt(restaurantId, new Location(45.5, -73.6));
        UUID courierId = idleCourier();

        dispatch.dispatchWaitingOrders();

        assertEquals(List.of(ready.getId()), queues.globalReadySnapshot());
        assertEquals(courierId, ready.getCourierId());
        assertNull(cancelled.getCourierId());
    }
    // A cancelled order at the front used to throw on assignment and stop every order behind it.

    @Test
    void ordersFromARestaurantWithNoLocationAreSkippedAndTheRestAreStillOffered() {
        UUID nowhere = UUID.randomUUID();
        Order stranded = stored("ReadyState", nowhere);
        Order ready = stored("ReadyState");
        restaurantAt(nowhere, null);
        restaurantAt(restaurantId, new Location(45.5, -73.6));
        UUID courierId = idleCourier();

        dispatch.dispatchWaitingOrders();

        assertNull(stranded.getCourierId());
        assertEquals(courierId, ready.getCourierId());
        assertEquals(List.of(stranded.getId(), ready.getId()), queues.globalReadySnapshot());
    }
    // Throwing on it used to stop every order behind it, and stopped the app starting if it was waiting then.
}
