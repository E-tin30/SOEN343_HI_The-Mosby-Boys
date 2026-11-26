package com.mosbyboys.dishesbecrazy.application.ntf;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;
import com.mosbyboys.dishesbecrazy.domain.ntf.NotificationChannel;

import lombok.RequiredArgsConstructor;

/* Builds a notification and hands it to every channel. Callers (OrderNotifier, DispatchWaitMonitor)
don't know which channels exist. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationChannel> channels;
    private final Clock clock;

    public void notify(UUID recipientId, String kind, String message, UUID orderId) {
        Notification notification = new Notification(
                UUID.randomUUID(), recipientId, kind, message, orderId, Instant.now(clock), false);
        channels.forEach(channel -> channel.deliver(notification));
    }
}