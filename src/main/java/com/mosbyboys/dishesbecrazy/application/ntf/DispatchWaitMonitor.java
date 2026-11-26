package com.mosbyboys.dishesbecrazy.application.ntf;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* R-NTF-02: alerts every operator once when an order has waited in the dispatch queue longer than the configured threshold.
The queue keeps no timestamps, so the first check that sees an order starts its clock. That also means startup and demo
restoration need no special handling: orders already in the queue are simply timed from the next check. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchWaitMonitor {

    static final String KIND = "DISPATCH_WAIT";

    private final OrderQueues queues;
    private final REGFacade registration;
    private final NotificationService notifications;
    private final NotificationProperties properties;
    private final Clock clock;

    private final Map<UUID, Instant> waitingSince = new HashMap<>();
    private final Set<UUID> alerted = new HashSet<>();

    @Scheduled(fixedDelayString = "#{@notificationProperties.monitorInterval.toMillis()}")
    public synchronized void check() {
        try {
            Instant now = clock.instant();
            Set<UUID> waiting = new HashSet<>(queues.globalReadySnapshot());

            // Forget orders that left the queue (accepted, cancelled...). If one comes back, its wait starts over.
            waitingSince.keySet().retainAll(waiting);
            alerted.retainAll(waiting);

            for (UUID orderId : waiting) {
                Duration waited = Duration.between(waitingSince.computeIfAbsent(orderId, id -> now), now);
                if (!alerted.contains(orderId) && waited.compareTo(properties.getDispatchWaitThreshold()) > 0) {
                    alertOperators(orderId, waited);
                    alerted.add(orderId);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Dispatch wait check failed; it will run again at the next interval", e);
        }
    }

    private void alertOperators(UUID orderId, Duration waited) {
        String message = "Order %s has been waiting for a courier for %s."
                .formatted(OrderNumber.of(orderId), describe(waited));
        List<UUID> operators = registration.getOperatorIds();
        operators.forEach(operatorId -> notifications.notify(operatorId, KIND, message, orderId));
    }

    private static String describe(Duration waited) {
        return waited.toMinutes() > 0 ? waited.toMinutes() + " min" : waited.toSeconds() + " s";
    }
}