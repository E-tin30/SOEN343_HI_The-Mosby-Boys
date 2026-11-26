package com.mosbyboys.dishesbecrazy.application.ntf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;

class NotificationStoreTest {

    private static final Instant START = Instant.parse("2026-11-25T12:00:00Z");

    private JdbcTemplate jdbc;
    private NotificationStore store;
    private UUID alice;
    private UUID bob;

    @BeforeEach
    void setUp() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("demo/restoration-schema.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        store = new NotificationStore(jdbc);
        alice = user("alice");
        bob = user("bob");
    }

    private UUID user(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id, email) VALUES (?, ?)", id, name + "@example.com");
        jdbc.update("INSERT INTO public.\"User\" (id, username, role) VALUES (?, ?, 'CUSTOMER')", id, name);
        return id;
    }

    private Notification note(UUID recipient, String message, int secondsAfterStart) {
        return new Notification(UUID.randomUUID(), recipient, "AcceptedState", message,
                UUID.randomUUID(), START.plusSeconds(secondsAfterStart), false);
    }

    @Test
    void roundTripsEveryField() {
        Notification saved = note(alice, "Tasty accepted your order.", 0);
        store.add(saved);

        List<Notification> listed = store.list(alice);

        assertEquals(1, listed.size());
        assertEquals(saved, listed.get(0));
    }

    @Test
    void listsNewestFirst() {
        store.add(note(alice, "first", 0));
        store.add(note(alice, "second", 10));
        store.add(note(alice, "third", 20));

        List<String> messages = store.list(alice).stream().map(Notification::message).toList();

        assertEquals(List.of("third", "second", "first"), messages);
    }

    @Test
    void keepsOnlyTheNewestFiftyPerUser() {
        for (int i = 0; i < 55; i++) {
            store.add(note(alice, "n" + i, i));
        }

        List<Notification> listed = store.list(alice);

        assertEquals(50, listed.size());
        assertEquals("n54", listed.get(0).message());
        assertEquals("n5", listed.get(49).message());
        assertEquals(50, jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.\"Notification\" WHERE user_id = ?", Integer.class, alice));
    }

    @Test
    void usersOnlySeeTheirOwnNotifications() {
        store.add(note(alice, "for alice", 0));
        store.add(note(bob, "for bob", 1));

        assertEquals(List.of("for alice"), store.list(alice).stream().map(Notification::message).toList());
        assertEquals(List.of("for bob"), store.list(bob).stream().map(Notification::message).toList());
    }

    @Test
    void markAllReadOnlyAffectsThatUser() {
        store.add(note(alice, "a1", 0));
        store.add(note(alice, "a2", 1));
        store.add(note(bob, "b1", 2));

        store.markAllRead(alice);

        assertTrue(store.list(alice).stream().allMatch(Notification::read));
        assertFalse(store.list(bob).get(0).read());
    }

    @Test
    void clearRemovesEveryonesNotifications() {
        store.add(note(alice, "a1", 0));
        store.add(note(bob, "b1", 1));

        store.clear();

        assertTrue(store.list(alice).isEmpty());
        assertTrue(store.list(bob).isEmpty());
    }
}