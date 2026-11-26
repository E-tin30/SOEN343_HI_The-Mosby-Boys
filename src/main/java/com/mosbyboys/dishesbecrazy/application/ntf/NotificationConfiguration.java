package com.mosbyboys.dishesbecrazy.application.ntf;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/* A Clock bean so time can be faked in tests, and scheduling switched on for DispatchWaitMonitor. */
@Configuration
@EnableScheduling
public class NotificationConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}