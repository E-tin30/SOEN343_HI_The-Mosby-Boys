package com.mosbyboys.dishesbecrazy.technical.seed;

import java.util.UUID;

import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.Account;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.annotation.Order;

// Loads demo accounts only when the demo-seed profile is active.
@Order(1)
@Component
@Profile("demo-seed")
public class DemoProfileSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DemoFixtureLoader fixtures;
    private final DemoAuthAdmin auth;

    public DemoProfileSeeder(JdbcTemplate jdbc, PlatformTransactionManager manager, DemoFixtureLoader fixtures, DemoAuthAdmin auth) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.fixtures = fixtures;
        this.auth = auth;
    }

    @Override
    public void run(String... args) throws Exception {
        var accounts = fixtures.load().accounts();
        // Loads validated profile/menu fixtures before contacting Auth or the database.

        // Reuses existing Auth accounts; creates missing ones before the SQL transaction.
        UUID[] userIds = new UUID[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) {
            var ids = jdbc.queryForList("SELECT id FROM auth.users WHERE email = ?",
                    UUID.class, accounts.get(i).email());
            if (ids.size() > 1) {
                throw new IllegalStateException("Multiple Auth users match " + accounts.get(i).email());
            }
            userIds[i] = ids.isEmpty() ? auth.create(accounts.get(i)) : ids.getFirst();
        }

        // Saves all public profiles together or rolls them all back.
        transaction.executeWithoutResult(status -> {
            for (int i = 0; i < accounts.size(); i++) {
                seed(accounts.get(i), userIds[i]);
            }
        });
        System.out.println("Demo profiles ready: CUSTOMER, RESTAURANT, COURIER, OPERATOR.");
    }

    private void seed(Account account, UUID userId) {
        // Adds the shared profile without overwriting an existing row.
        jdbc.update("""
                INSERT INTO public."User" (id, first_name, last_name, username, role)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, userId, account.firstName(), account.lastName(), account.username(), account.role().name());
        // Stops if an existing profile has a conflicting role.
        String role = jdbc.queryForObject("SELECT role FROM public.\"User\" WHERE id = ?", String.class, userId);
        if (!account.role().name().equals(role)) {
            throw new IllegalStateException("Existing demo profile has a different role: " + account.email());
        }
        // Adds the role-specific row, keyed by the user's id, only if that user has none.
        switch (account.role()) {
            case CUSTOMER -> jdbc.update("""
                    INSERT INTO public."Customer" (user_id, created_at, address, latitude, longitude)
                    VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?) ON CONFLICT (user_id) DO NOTHING
                    """, userId, account.address(), account.latitude(), account.longitude());
            case COURIER -> jdbc.update("""
                    INSERT INTO public."Courier" (user_id, created_at, state, latitude, longitude)
                    VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?) ON CONFLICT (user_id) DO NOTHING
                    """, userId, account.state(), account.latitude(), account.longitude());
            case RESTAURANT -> jdbc.update("""
                    INSERT INTO public."Restaurant" (user_id, created_at, name, status, latitude, longitude)
                    VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?) ON CONFLICT (user_id) DO NOTHING
                    """, userId, account.restaurantName(), account.status(),
                    account.latitude(), account.longitude());
            case OPERATOR -> { /* Operators need only their Users row. */ }
        }
    }
}
