package com.mosbyboys.dishesbecrazy.technical.seed;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DemoNotificationSeeder {
    private static final String ORDER_PREFIX = "34310000-0000-4000-8000-000000000";

    private record Seed(String orderSuffix, String kind, String message, String createdAt, boolean read) {
    }

    // One notification per finished demo order, matching OrderNotifier's wording. Only the Ready one is unread.
    private static final List<Seed> CUSTOMER_NOTIFICATIONS = List.of(
            new Seed("215", "ReadyState",
                    "Your order #000215 at Basil & Bloom is ready and waiting for a courier.",
                    "2026-01-15T19:15:00Z", false),
            new Seed("214", "RefundedState",
                    "Your order #000214 from La Petite Trattoria was refunded.",
                    "2026-01-14T15:36:00Z", true),
            new Seed("213", "CancelledState",
                    "Your order #000213 at La Petite Trattoria was cancelled.",
                    "2026-01-14T14:01:00Z", true),
            new Seed("212", "RejectedState",
                    "La Petite Trattoria rejected your order #000212.",
                    "2026-01-14T13:02:00Z", true),
            new Seed("211", "DeliveredState",
                    "Your order #000211 from La Petite Trattoria was delivered.",
                    "2026-01-14T12:35:00Z", true));

    void seed(Map<String, UUID> users) {
        UUID customer = users.get("demo_customer");
        for (var n : CUSTOMER_NOTIFICATIONS) {
            jdbc.update("""
                    INSERT INTO public."Notification" (id, user_id, order_id, kind, message, created_at, is_read)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), customer, UUID.fromString(ORDER_PREFIX + n.orderSuffix()),
                    n.kind(), n.message(), Timestamp.from(Instant.parse(n.createdAt())), n.read());
        }
    }
    // Ids and order numbers mirror demo/orders.json; the order id has no FK so a mismatch would only break the link.

    private final JdbcTemplate jdbc;
}