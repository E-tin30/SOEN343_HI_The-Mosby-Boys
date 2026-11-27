package com.mosbyboys.dishesbecrazy.ui.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mosbyboys.dishesbecrazy.application.ntf.NTFFacade;
import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;

import lombok.RequiredArgsConstructor;

/* The logged-in user's own notifications. Every user (customer, restaurant, courier, operator) only ever sees their own list. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NTFFacade ntfFacade;

    public record NotificationResponse(UUID id, String kind, String message, UUID orderId, Instant createdAt, boolean read) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.id(), n.kind(), n.message(), n.orderId(), n.createdAt(), n.read());
        }
    }

    // Newest first
    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return ntfFacade.list(UUID.fromString(jwt.getSubject())).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    // Called when the user opens the notification center
    @PostMapping("/read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        ntfFacade.markAllRead(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}