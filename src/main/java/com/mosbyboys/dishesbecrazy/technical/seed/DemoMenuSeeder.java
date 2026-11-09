package com.mosbyboys.dishesbecrazy.technical.seed;

import java.util.UUID;

import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.MenuSeed;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.annotation.Order;

@Order(2)
@Component
@RequiredArgsConstructor
@Profile("demo-seed")
public class DemoMenuSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DemoFixtureLoader fixtures;

    @Override
    public void run(String... args) throws Exception {
        var items = fixtures.load().menuItems();
        // Reuses the same validated snapshot shape as profile seeding and future restoration.

        transaction.executeWithoutResult(status -> {
            for (MenuSeed item : items) {
                seed(item, restaurantIdFor(item.restaurantUsername()));
            }
        });
        System.out.println("Demo menu items ready.");
    }

    // A restaurant's id is its user id, which Supabase Auth assigns when the profile seeder runs,
    // so menu.json names the restaurant by username instead.
    private UUID restaurantIdFor(String username) {
        var ids = jdbc.queryForList("""
                SELECT r.user_id FROM public."Restaurant" r
                JOIN public."User" u ON u.id = r.user_id WHERE u.username = ?
                """, UUID.class, username);
        if (ids.size() != 1) {
            throw new IllegalStateException("No single demo restaurant has username " + username);
        }
        return ids.getFirst();
    }

    private void seed(MenuSeed item, UUID restaurantId) {
        jdbc.update("""
                INSERT INTO public."MenuItem" (id, restaurant_id, name, price, stock_quantity, status, description)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, item.id(), restaurantId, item.name(), item.price(), item.stockQuantity(), item.status().name(), item.description());
    }
}
