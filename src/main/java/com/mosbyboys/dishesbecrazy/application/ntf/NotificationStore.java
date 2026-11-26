package com.mosbyboys.dishesbecrazy.application.ntf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;

/* Short per-user list of notifications, kept in memory. Each user keeps only the newest MAX_PER_USER;
older ones fall off. Shared by every request, so the map is concurrent and each list is locked while it changes. */
@Component
public class NotificationStore {

    static final int MAX_PER_USER = 50;

    // Newest first.
    private final Map<UUID, Deque<Notification>> byUser = new ConcurrentHashMap<>();

    public void add(Notification notification) {
        Deque<Notification> list = byUser.computeIfAbsent(notification.recipientId(), id -> new ConcurrentLinkedDeque<>());
        synchronized (list) {
            list.addFirst(notification);
            while (list.size() > MAX_PER_USER) {
                list.removeLast();
            }
        }
    }

    // The user's notifications, newest first. A copy, so callers can't change the store.
    public List<Notification> list(UUID userId) {
        Deque<Notification> list = byUser.get(userId);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return Collections.unmodifiableList(new ArrayList<>(list));
        }
    }

    public void markAllRead(UUID userId) {
        Deque<Notification> list = byUser.get(userId);
        if (list == null) {
            return;
        }
        synchronized (list) {
            List<Notification> read = list.stream().map(Notification::withRead).toList();
            list.clear();
            list.addAll(read);
        }
    }

    // Used when the demo is restored, so old messages don't outlive the data they describe.
    public void clear() {
        byUser.clear();
    }
}