package com.mosbyboys.dishesbecrazy.application.ntf;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;

// Notifications persisted per user, newest first, capped at MAX_PER_USER.
// add() runs inside the caller's transaction, so a notification for an order
// that rolls back is rolled back with it.
@Component
public class NotificationStore {

    static final int MAX_PER_USER = 50;

    private static final RowMapper<Notification> MAPPER = (rs, i) -> new Notification(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getString("kind"),
            rs.getString("message"),
            rs.getObject("order_id", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getBoolean("is_read"));

    private final JdbcTemplate jdbc;

    public NotificationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(Notification notification) {
        jdbc.update("""
                INSERT INTO public."Notification" (id, user_id, order_id, created_at, kind, message, is_read)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                notification.id(), notification.userId(), notification.orderId(),
                Timestamp.from(notification.createdAt()), notification.kind(),
                notification.message(), notification.read());

        // Keep only the newest MAX_PER_USER for this user.
        jdbc.update("""
                DELETE FROM public."Notification"
                WHERE user_id = ?
                  AND id NOT IN (
                      SELECT id FROM public."Notification"
                      WHERE user_id = ?
                      ORDER BY created_at DESC, id
                      LIMIT ?)
                """,
                notification.userId(), notification.userId(), MAX_PER_USER);
    }

    // Newest first.
    public List<Notification> list(UUID userId) {
        return jdbc.query("""
                SELECT id, user_id, order_id, created_at, kind, message, is_read
                FROM public."Notification"
                WHERE user_id = ?
                ORDER BY created_at DESC, id
                LIMIT ?
                """, MAPPER, userId, MAX_PER_USER);
    }

    public void markAllRead(UUID userId) {
        jdbc.update("""
                UPDATE public."Notification" SET is_read = true
                WHERE user_id = ? AND is_read = false
                """, userId);
    }

    public void clear() {
        jdbc.update("DELETE FROM public.\"Notification\"");
    }
}