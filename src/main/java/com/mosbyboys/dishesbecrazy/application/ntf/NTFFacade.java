package com.mosbyboys.dishesbecrazy.application.ntf;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;

import lombok.RequiredArgsConstructor;

/* The only way other modules and the UI reach NTF */
@Service
@RequiredArgsConstructor
public class NTFFacade {

    private final NotificationStore store;

    public List<Notification> list(UUID userId) {
        return store.list(userId);
    }

    public void markAllRead(UUID userId) {
        store.markAllRead(userId);
    }

    // Used when the demo is restored.
    public void clearAll() {
        store.clear();
    }
}