package com.mosbyboys.dishesbecrazy.application.ntf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;
import com.mosbyboys.dishesbecrazy.domain.ntf.NotificationChannel;

class NotificationServiceTest {

    private final Instant now = Instant.parse("2026-11-25T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void deliversTheSameNotificationToEveryChannel() {
        NotificationChannel first = mock(NotificationChannel.class);
        NotificationChannel second = mock(NotificationChannel.class);
        NotificationService service = new NotificationService(List.of(first, second), clock);
        UUID recipient = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        service.notify(recipient, "AcceptedState", "Tasty accepted your order", orderId);

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(first).deliver(sent.capture());
        verify(second).deliver(sent.getValue());
        assertEquals(recipient, sent.getValue().recipientId());
        assertEquals("AcceptedState", sent.getValue().kind());
        assertEquals("Tasty accepted your order", sent.getValue().message());
        assertEquals(orderId, sent.getValue().orderId());
        assertEquals(now, sent.getValue().createdAt());
        assertFalse(sent.getValue().read());
    }

    @Test
    void inlineChannelKeepsTheNotificationInTheStore() {
        NotificationStore store = new NotificationStore();
        NotificationService service = new NotificationService(List.of(new InlineNotificationChannel(store)), clock);
        UUID recipient = UUID.randomUUID();

        service.notify(recipient, "DeliveredState", "Delivered", null);

        assertEquals(1, store.list(recipient).size());
        assertEquals("Delivered", store.list(recipient).get(0).message());
    }

    @Test
    void worksWithNoChannels() {
        NotificationService service = new NotificationService(List.of(), clock);

        assertDoesNotThrow(() -> service.notify(UUID.randomUUID(), "k", "m", null));
    }
}