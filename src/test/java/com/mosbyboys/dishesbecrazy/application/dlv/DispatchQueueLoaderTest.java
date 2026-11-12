package com.mosbyboys.dishesbecrazy.application.dlv;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;

class DispatchQueueLoaderTest {

    private final OrderService orderService = mock(OrderService.class);
    private final CourierService courierService = mock(CourierService.class);
    private final DispatchService dispatchService = mock(DispatchService.class);
    private final OrderQueues queues = new OrderQueues();
    private final DispatchQueueLoader loader = new DispatchQueueLoader(orderService, queues, courierService, dispatchService);

    private Order ready(UUID courierId) {
        return Order.reconstitute(UUID.randomUUID(), OrderStates.fromName("ReadyState"), UUID.randomUUID(),
                UUID.randomUUID(), courierId, List.of(), Instant.now(), null, BigDecimal.ZERO);
    }

    @Test
    void rebuildingReplacesStaleIdsWithTheReadyOrdersStillWaiting() {
        UUID stale = UUID.randomUUID();
        queues.enqueueGlobalReady(stale);
        UUID carryingCourier = UUID.randomUUID();
        Order waiting = ready(null);
        Order accepted = ready(carryingCourier);
        when(courierService.getOnlineCouriers())
                .thenReturn(List.of(new CourierData(carryingCourier, CourierState.CARRYING, 45.5, -73.6)));
        when(orderService.findInStates(any())).thenReturn(List.of(waiting, accepted));

        loader.rebuild();

        assertEquals(List.of(waiting.getId()), queues.globalReadySnapshot());
        verifyNoInteractions(dispatchService);
    }
    // An accepted order has left the queue; the stale ID would make the next dispatch fail to load it.

    @Test
    void aFailedReadLeavesTheCurrentQueueIntact() {
        UUID queued = UUID.randomUUID();
        queues.enqueueGlobalReady(queued);
        when(orderService.findInStates(any())).thenThrow(new DataAccessResourceFailureException("Orders unavailable"));

        assertThrows(DataAccessResourceFailureException.class, loader::rebuild);

        assertEquals(List.of(queued), queues.globalReadySnapshot());
    }

    @Test
    void startupRebuildsThenDispatches() {
        when(orderService.findInStates(any())).thenReturn(List.of());

        loader.run();

        verify(dispatchService).dispatchWaitingOrders();
    }
}
