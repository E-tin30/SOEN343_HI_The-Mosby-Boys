package com.mosbyboys.dishesbecrazy.technical.seed;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

// Loads demo accounts only when the demo-seed profile is active.
@Component
@Profile("demo-seed")
public class DemoProfileSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final DemoAuthAdmin auth;

    public DemoProfileSeeder(JdbcTemplate jdbc, PlatformTransactionManager manager, JsonMapper json, DemoAuthAdmin auth) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.json = json;
        this.auth = auth;
    }

    @Override
    public void run(String... args) throws Exception {
        // Loads the four fictional profiles from JSON.
        Account[] accounts;
        try (var input = new ClassPathResource("demo/profiles.json").getInputStream()) {
            accounts = json.readValue(input, Account[].class);
        }
        validate(accounts);

        // Reuses existing Auth accounts; creates missing ones before the SQL transaction.
        UUID[] userIds = new UUID[accounts.length];
        for (int i = 0; i < accounts.length; i++) {
            var ids = jdbc.queryForList("SELECT id FROM auth.users WHERE email = ?",
                    UUID.class, accounts[i].email());
            if (ids.size() > 1) {
                throw new IllegalStateException("Multiple Auth users match " + accounts[i].email());
            }
            userIds[i] = ids.isEmpty() ? auth.create(accounts[i]) : ids.getFirst();
        }

        // Saves all public profiles together or rolls them all back.
        transaction.executeWithoutResult(status -> {
            for (int i = 0; i < accounts.length; i++) {
                seed(accounts[i], userIds[i]);
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
        // Adds the role-specific row only if that user has none.
        switch (account.role()) {
            case CUSTOMER -> jdbc.update("""
                    INSERT INTO public."Customer" (id, created_at, user_id, address)
                    SELECT ?, CURRENT_TIMESTAMP, ?, ?
                    WHERE NOT EXISTS (SELECT 1 FROM public."Customer" WHERE user_id = ?)
                    """, account.profileId(), userId, account.address(), userId);
            case COURIER -> jdbc.update("""
                    INSERT INTO public."Courier" (id, created_at, user_id, state, location)
                    SELECT ?, CURRENT_TIMESTAMP, ?, ?, ?
                    WHERE NOT EXISTS (SELECT 1 FROM public."Courier" WHERE user_id = ?)
                    """, account.profileId(), userId, account.state(), account.location(), userId);
            case RESTAURANT -> jdbc.update("""
                    INSERT INTO public."Restaurant" (id, created_at, user_id, name, status, location)
                    SELECT ?, CURRENT_TIMESTAMP, ?, ?, ?, ?
                    WHERE NOT EXISTS (SELECT 1 FROM public."Restaurant" WHERE user_id = ?)
                    """, account.profileId(), userId, account.restaurantName(), account.status(), account.location(), userId);
            case OPERATOR -> { /* Operators need only their User row. */ }
        }
    }

    // Requires one unique demo account per role and IDs for linked profiles.
    private void validate(Account[] accounts) {
        var roles = EnumSet.noneOf(Role.class);
        var emails = new HashSet<String>();
        for (Account account : accounts) {
            if (account.email() == null || !account.email().endsWith(".demo@example.com")
                    || !emails.add(account.email()) || account.role() == null || !roles.add(account.role())) {
                throw new IllegalArgumentException("Seed configuration requires unique demo emails and roles.");
            }
            if (account.role() != Role.OPERATOR && account.profileId() == null) {
                throw new IllegalArgumentException("Role-specific profiles require a UUID.");
            }
        }
        if (roles.size() != Role.values().length) {
            throw new IllegalArgumentException("Seed configuration requires one account of each role.");
        }
    }

    // Holds the JSON fields; passwords stay in the environment.
    public record Account(String email, String firstName, String lastName, String username,
                          Role role, UUID profileId, String address, String state, String location,
                          String restaurantName, String status) {
    }
}
