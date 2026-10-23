package com.mosbyboys.dishesbecrazy.technical.seed;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.annotation.Order;

import tools.jackson.databind.json.JsonMapper;

@Order(2)
@Component
@RequiredArgsConstructor
@Profile("demo-seed")
public class DemoMenuSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JsonMapper json;

    @Override
    public void run(String... args) throws Exception {
        MenuSeed[] items;
        try (var input = new ClassPathResource("demo/menu.json").getInputStream()) {
            items = json.readValue(input, MenuSeed[].class);
        }
        validate(items);

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

    void validate(MenuSeed[] items) {
        if (items.length == 0) {
            throw new IllegalArgumentException("Menu seed configuration has no items.");
        }
        var ids = new HashSet<UUID>();
        for (MenuSeed item : items) {
            if (item.id() == null || !ids.add(item.id())) {
                throw new IllegalArgumentException("Menu items require a unique id.");
            }
            if (item.restaurantUsername() == null || item.restaurantUsername().isBlank()) {
                throw new IllegalArgumentException("Menu item " + item.id() + " requires a restaurantUsername.");
            }
            if (item.name() == null || item.name().isBlank()) {
                throw new IllegalArgumentException("Menu item " + item.id() + " requires a name.");
            }
            if (item.price() == null || item.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Menu item " + item.name() + " requires a price above 0.");
            }
            if (item.stockQuantity() < 0) {
                throw new IllegalArgumentException("Menu item " + item.name() + " cannot have negative stock.");
            }
            if (item.status() == null) {
                throw new IllegalArgumentException("Menu item " + item.name() + " requires a status.");
            }
            if (item.status() == MenuItemStatus.AVAILABLE && item.stockQuantity() == 0) {
                throw new IllegalArgumentException("Menu item " + item.name() + " is AVAILABLE with no stock.");
            }
        }
    }

    public record MenuSeed(UUID id, String restaurantUsername, String name, BigDecimal price,
                       int stockQuantity, MenuItemStatus status, String description) {}
}
