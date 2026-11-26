package com.mosbyboys.dishesbecrazy.application.ntf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;

class NotificationStoreTest {

    private final NotificationStore store = new NotificationStore();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private static Notification note(UUID recipient, String message) {
        return new Notification(UUID.randomUUID(), recipient, "AcceptedState", message, null, Instant.now(), false);
    }

    @Test
    void listsNewestFirst() {
        store.add(note(alice, "first"));
        store.add(note(alice, "second"));

        List<Notification> list = store.list(alice);

        assertEquals("second", list.get(0).message());
        assertEquals("first", list.get(1).message());
    }

    @Test
    void keepsOnlyTheNewestFifty() {
        for (int i = 0; i < NotificationStore.MAX_PER_USER + 5; i++) {
            store.add(note(alice, "n" + i));
        }

        List<Notification> list = store.list(alice);

        assertEquals(NotificationStore.MAX_PER_USER, list.size());
        assertEquals("n" + (NotificationStore.MAX_PER_USER + 4), list.get(0).message());
        assertEquals("n5", list.get(list.size() - 1).message());
    }

    @Test
    void keepsEachUsersListSeparate() {
        store.add(note(alice, "for alice"));

        assertEquals(1, store.list(alice).size());
        assertTrue(store.list(bob).isEmpty());
    }

    @Test
    void markAllReadOnlyAffectsThatUser() {
        store.add(note(alice, "a"));
        store.add(note(bob, "b"));

        store.markAllRead(alice);

        assertTrue(store.list(alice).get(0).read());
        assertFalse(store.list(bob).get(0).read());
    }

    @Test
    void markAllReadOnAnUnknownUserDoesNothing() {
        store.markAllRead(UUID.randomUUID());
    }

    @Test
    void clearRemovesEveryonesNotifications() {
        store.add(note(alice, "a"));
        store.add(note(bob, "b"));

        store.clear();

        assertTrue(store.list(alice).isEmpty());
        assertTrue(store.list(bob).isEmpty());
    }
}