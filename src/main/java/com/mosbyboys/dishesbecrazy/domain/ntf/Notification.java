package com.mosbyboys.dishesbecrazy.domain.ntf;

import java.time.Instant;
import java.util.UUID;

/* One message for one user (R-NTF-01, R-NTF-02). Immutable: marking it read makes a copy.
kind is the order state that caused it, or DISPATCH_WAIT for the operator alert. orderId may be null. */
public record Notification(
        UUID id,
        UUID userId,
        String kind,
        String message,
        UUID orderId,
        Instant createdAt,
        boolean read
) {

    public Notification withRead() {
        return read ? this : new Notification(id, userId, kind, message, orderId, createdAt, true);
    }
}