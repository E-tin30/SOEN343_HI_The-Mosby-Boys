package com.mosbyboys.dishesbecrazy.application.ntf;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;
import com.mosbyboys.dishesbecrazy.domain.ntf.NotificationChannel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* Builds a notification and hands it to every channel. Callers (OrderNotifier, DispatchWaitMonitor)
don't know which channels exist. Inside an order transaction, delivery waits until that transaction has
committed, so a failed notification can never undo the order change. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationChannel> channels;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    public void notify(UUID userId, String kind, String message, UUID orderId) {
        Notification notification = new Notification(
                UUID.randomUUID(), userId, kind, message, orderId, Instant.now(clock), false);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(notification);
                }
            });
        } else {
            deliver(notification);
        }
    }

    private void deliver(Notification notification) {
        try {
            // REQUIRES_NEW is required: after commit, the old transaction no longer commits anything new.
            var transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status -> channels.forEach(channel -> channel.deliver(notification)));
        } catch (RuntimeException e) {
            log.warn("Could not deliver notification {} to {}", notification.id(), notification.userId(), e);
        }
    }
}