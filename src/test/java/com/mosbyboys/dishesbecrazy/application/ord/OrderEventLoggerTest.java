package com.mosbyboys.dishesbecrazy.application.ord;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;

class OrderEventLoggerTest {

    @Test
    void savesOneEventRowForAStateChange() {
        OrderEventService service = mock(OrderEventService.class);
        OrderEventLogger logger = new OrderEventLogger(service);
        UUID orderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        logger.update(new OrderStateChanged(orderId, "PlacedState", "AcceptedState", actorId, "ACCEPT"));

        verify(service).save(orderId, "PlacedState", "AcceptedState", actorId, "ACCEPT");
    }
}