package com.mosbyboys.dishesbecrazy.application.ntf;

import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ntf.Notification;
import com.mosbyboys.dishesbecrazy.domain.ntf.NotificationChannel;

import lombok.RequiredArgsConstructor;

/* The only channel for now: keeps the message in the user's list, which the dashboard shows as a toast and in the notification center. */
@Component
@RequiredArgsConstructor
public class InlineNotificationChannel implements NotificationChannel {

    private final NotificationStore store;

    @Override
    public void deliver(Notification notification) {
        store.add(notification);
    }
}