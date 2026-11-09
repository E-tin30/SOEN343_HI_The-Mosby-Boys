package com.mosbyboys.dishesbecrazy.application.ord;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;

class OrderEventLoggerTest {

    private final UUID orderId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Test
    void savesOneEventRowForAStateChange() {
        OrderEventService service = mock(OrderEventService.class);
        OrderEventLogger logger = new OrderEventLogger(service);

        logger.onOrderStateChanged(
                new OrderStateChanged(orderId, "PlacedState", "AcceptedState", actorId, "ACCEPT"));

        verify(service).save(orderId, "PlacedState", "AcceptedState", actorId, "ACCEPT");
    }

    // Proves the @EventListener wiring: a published event really reaches the observer.
    @Test
    void springDeliversPublishedEventsToTheLogger() {
        OrderEventService service = mock(OrderEventService.class);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(OrderEventService.class, () -> service);
            context.register(OrderEventLogger.class);
            context.refresh();

            context.publishEvent(
                    new OrderStateChanged(orderId, null, "PlacedState", actorId, "PLACE"));
        }

        verify(service).save(orderId, null, "PlacedState", actorId, "PLACE");
    }
}