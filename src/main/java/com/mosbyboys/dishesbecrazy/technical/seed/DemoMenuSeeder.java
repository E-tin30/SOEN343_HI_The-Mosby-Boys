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
        // Loads the four fictional profiles from JSON.
        MenuSeed[] items;
        try (var input = new ClassPathResource("demo/menu.json").getInputStream()) {
            items = json.readValue(input, MenuSeed[].class);
        }
        validate(items);

        transaction.executeWithoutResult(status -> {
            for (MenuSeed item : items) {
                seed(item);
            }
        });
        System.out.println("Demo menu items ready.");
    }

    private void seed(MenuSeed item) {
        jdbc.update("""
                INSERT INTO public."MenuItem" (id, restaurant_id, name, price, stock_quantity, status, description)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, item.id(), item.restaurantId(), item.name(), item.price(), item.stockQuantity(), item.status().name(), item.description());
    }

    // Requires valid, uniquely identified menu items before anything is inserted.
    private void validate(MenuSeed[] items) {
        if (items.length == 0) {
            throw new IllegalArgumentException("Menu seed configuration has no items.");
        }
        var ids = new HashSet<UUID>();
        for (MenuSeed item : items) {
            if (item.id() == null || !ids.add(item.id())) {
                throw new IllegalArgumentException("Menu items require a unique id.");
            }
            if (item.restaurantId() == null) {
                throw new IllegalArgumentException("Menu item " + item.id() + " requires a restaurantId.");
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

    public record MenuSeed(UUID id, UUID restaurantId, String name, BigDecimal price,
                       int stockQuantity, MenuItemStatus status, String description) {}

}
