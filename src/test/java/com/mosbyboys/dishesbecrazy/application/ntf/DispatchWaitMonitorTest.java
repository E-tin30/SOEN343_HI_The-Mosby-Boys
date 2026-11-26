package com.mosbyboys.dishesbecrazy.application.ntf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;

class DispatchWaitMonitorTest {

    // A clock the test moves by hand.
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-25T12:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final UUID orderId = UUID.randomUUID();
    private final UUID operatorOne = UUID.randomUUID();
    private final UUID operatorTwo = UUID.randomUUID();

    private MutableClock clock;
    private OrderQueues queues;
    private REGFacade registration;
    private NotificationService notifications;
    private DispatchWaitMonitor monitor;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        queues = new OrderQueues();
        registration = mock(REGFacade.class);
        notifications = mock(NotificationService.class);
        NotificationProperties properties = new NotificationProperties();
        properties.setDispatchWaitThreshold(Duration.ofMinutes(5));
        when(registration.getOperatorIds()).thenReturn(List.of(operatorOne, operatorTwo));
        monitor = new DispatchWaitMonitor(queues, registration, notifications, properties, clock);
    }

    @Test
    void doesNotAlertBeforeTheThreshold() {
        queues.enqueueGlobalReady(orderId);
        monitor.check();

        clock.advance(Duration.ofMinutes(4));
        monitor.check();

        verifyNoInteractions(notifications);
    }

    @Test
    void alertsEveryOperatorOnceAfterTheThreshold() {
        queues.enqueueGlobalReady(orderId);
        monitor.check();

        clock.advance(Duration.ofMinutes(6));
        monitor.check();
        clock.advance(Duration.ofMinutes(1));
        monitor.check();

        verify(notifications, times(1)).notify(eq(operatorOne), eq("DISPATCH_WAIT"), contains("waiting"), eq(orderId));
        verify(notifications, times(1)).notify(eq(operatorTwo), eq("DISPATCH_WAIT"), contains("waiting"), eq(orderId));
    }

    @Test
    void theClockStartsAtTheFirstCheckThatSeesTheOrder() {
        queues.enqueueGlobalReady(orderId);
        clock.advance(Duration.ofMinutes(10));

        monitor.check();

        verifyNoInteractions(notifications);
    }

    @Test
    void forgetsAnOrderThatLeftTheQueueAndTimesItAgainIfItReturns() {
        queues.enqueueGlobalReady(orderId);
        monitor.check();
        clock.advance(Duration.ofMinutes(6));
        queues.removeGlobalReady(orderId);
        monitor.check();

        queues.enqueueGlobalReady(orderId);
        monitor.check();
        verifyNoInteractions(notifications);

        clock.advance(Duration.ofMinutes(6));
        monitor.check();

        verify(notifications, times(1)).notify(eq(operatorOne), eq("DISPATCH_WAIT"), contains("waiting"), eq(orderId));
    }

    @Test
    void aFailureLookingUpOperatorsDoesNotEscape() {
        when(registration.getOperatorIds()).thenThrow(new IllegalStateException("database unavailable"));
        queues.enqueueGlobalReady(orderId);
        monitor.check();
        clock.advance(Duration.ofMinutes(6));

        assertDoesNotThrow(monitor::check);
    }

    @Test
    void resetMakesTheMonitorAlertAgainForAnOrderStillInTheQueue() {
        queues.enqueueGlobalReady(orderId);
        monitor.check();
        clock.advance(Duration.ofMinutes(6));
        monitor.check();
        verify(notifications, times(1)).notify(eq(operatorOne), eq("DISPATCH_WAIT"), contains("waiting"), eq(orderId));

        monitor.reset();
        monitor.check();
        clock.advance(Duration.ofMinutes(6));
        monitor.check();

        verify(notifications, times(2)).notify(eq(operatorOne), eq("DISPATCH_WAIT"), contains("waiting"), eq(orderId));
    }
}