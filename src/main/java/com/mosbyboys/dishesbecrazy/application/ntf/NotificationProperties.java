package com.mosbyboys.dishesbecrazy.application.ntf;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/* The notifications.* settings in application.yaml */
@Component
@ConfigurationProperties(prefix = "notifications")
@Getter
@Setter
public class NotificationProperties {

    // How long an order may sit in the dispatch queue before operators are alerted (R-NTF-02).
    private Duration dispatchWaitThreshold = Duration.ofMinutes(5);

    // How often the queue is checked.
    private Duration monitorInterval = Duration.ofSeconds(15);
}