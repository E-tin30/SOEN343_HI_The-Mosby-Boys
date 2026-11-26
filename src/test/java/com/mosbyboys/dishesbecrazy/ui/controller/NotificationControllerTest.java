package com.mosbyboys.dishesbecrazy.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import com.mosbyboys.dishesbecrazy.application.ntf.NTFFacade;
import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;
import com.mosbyboys.dishesbecrazy.ui.controller.NotificationController.NotificationResponse;

class NotificationControllerTest {

    private final UUID userId = UUID.randomUUID();
    private final NTFFacade facade = mock(NTFFacade.class);
    private final NotificationController controller = new NotificationController(facade);

    private Jwt jwtFor(UUID subject) {
        return Jwt.withTokenValue("token").header("alg", "none").subject(subject.toString()).build();
    }

    @Test
    void listsTheCallersNotifications() {
        UUID orderId = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-25T12:00:00Z");
        Notification note = new Notification(UUID.randomUUID(), userId, "AcceptedState", "Tasty accepted your order.", orderId, at, false);
        when(facade.list(userId)).thenReturn(List.of(note));

        List<NotificationResponse> response = controller.list(jwtFor(userId));

        assertEquals(1, response.size());
        assertEquals(note.id(), response.get(0).id());
        assertEquals("AcceptedState", response.get(0).kind());
        assertEquals("Tasty accepted your order.", response.get(0).message());
        assertEquals(orderId, response.get(0).orderId());
        assertEquals(at, response.get(0).createdAt());
        assertFalse(response.get(0).read());
    }

    @Test
    void listIsEmptyWhenThereAreNone() {
        when(facade.list(userId)).thenReturn(List.of());

        assertTrue(controller.list(jwtFor(userId)).isEmpty());
    }

    @Test
    void markAllReadActsOnTheCallersListAndReturnsNoContent() {
        ResponseEntity<Void> response = controller.markAllRead(jwtFor(userId));

        verify(facade).markAllRead(userId);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}