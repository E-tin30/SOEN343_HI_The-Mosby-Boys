package com.mosbyboys.dishesbecrazy.technical.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;

@Order(3)
@Component
@Profile("demo-seed")
@RequiredArgsConstructor
public class DemoOrderSeeder implements CommandLineRunner {
    private final DemoOrderStore orders;
    private final TransactionTemplate transaction;
    private final DemoFixtureLoader fixtures;

    @Override
    public void run(String... args) throws Exception {
        DemoFixtures baseline = fixtures.load();
        transaction.executeWithoutResult(status -> {
            var users = orders.resolveUsers(baseline);
            for (var order : baseline.orders()) {
                orders.seedMissing(order, users);
            }
        });
        System.out.println("Demo orders and event history ready.");
    }
    // Profiles and menus run first; all new orders, lines and events commit together before startup queues load.
}
