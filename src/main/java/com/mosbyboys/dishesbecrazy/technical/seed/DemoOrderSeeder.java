package com.mosbyboys.dishesbecrazy.technical.seed;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.OrderSeed;

import lombok.RequiredArgsConstructor;

@Order(3)
@Component
@Profile("demo-seed")
@RequiredArgsConstructor
public class DemoOrderSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DemoFixtureLoader fixtures;

    @Override
    public void run(String... args) throws Exception {
        DemoFixtures baseline = fixtures.load();
        transaction.executeWithoutResult(status -> {
            Map<String, UUID> users = resolveUsers(baseline);
            for (OrderSeed order : baseline.orders()) {
                seed(order, users);
            }
        });
        System.out.println("Demo orders and event history ready.");
    }
    // Profiles and menus run first; all new orders, lines and events commit together before startup queues load.

    private Map<String, UUID> resolveUsers(DemoFixtures baseline) {
        var users = new HashMap<String, UUID>();
        for (var account : baseline.accounts()) {
            var ids = jdbc.queryForList("SELECT id FROM public.\"User\" WHERE username = ? AND role = ?",
                    UUID.class, account.username(), account.role().name());
            Assert.state(ids.size() == 1, "No single demo " + account.role() + " profile has username " + account.username());
            users.put(account.username(), ids.getFirst());
        }
        return users;
    }
    // Resolve established identities rather than inventing Auth UUIDs or provisioning accounts during order insertion.

    private void seed(OrderSeed order, Map<String, UUID> users) {
        UUID customerId = users.get(order.customerUsername());
        UUID restaurantId = users.get(order.restaurantUsername());
        var existing = jdbc.queryForList("SELECT user_id, restaurant_id FROM public.\"Order\" WHERE id = ?", order.id());
        if (!existing.isEmpty()) {
            Assert.state(customerId.equals(existing.getFirst().get("user_id"))
                            && restaurantId.equals(existing.getFirst().get("restaurant_id")),
                    "Demo order ID already belongs to a different customer or restaurant: " + order.id());
            return;
        }
        // Skip the entire existing order, so a repeat run cannot append old events to an order that has progressed.

        for (var line : order.lines()) {
            var owners = jdbc.queryForList("SELECT restaurant_id FROM public.\"MenuItem\" WHERE id = ?",
                    UUID.class, line.menuItemId());
            Assert.state(owners.size() == 1 && restaurantId.equals(owners.getFirst()),
                    "Demo menu item is missing or belongs to another restaurant: " + line.menuItemId());
        }
        // The stored menu must match fixture ownership even when insert-only menu seeding skipped an existing ID.

        jdbc.update("""
                INSERT INTO public."Order" (id, user_id, restaurant_id, courier_id, state, created_at, subtotal, rejection_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, order.id(), customerId, restaurantId,
                order.courierUsername() == null ? null : users.get(order.courierUsername()),
                order.state(), Timestamp.from(order.placedAt()), order.subtotal(), order.rejectionReason());
        for (var line : order.lines()) {
            jdbc.update("""
                    INSERT INTO public."OrderLineItem" (id, order_id, menu_item_id, quantity, unit_price, menu_item_name, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, line.id(), order.id(), line.menuItemId(), line.quantity(), line.unitPrice(),
                    line.menuItemName(), Timestamp.from(order.placedAt()));
        }
        for (var event : order.events()) {
            jdbc.update("""
                    INSERT INTO public."Event" (id, order_id, performed_by, action, previous_state, new_state, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, event.id(), order.id(), users.get(event.performedByUsername()), event.action(),
                    event.previousState(), event.newState(), Timestamp.from(event.createdAt()));
        }
    }
    // Copy historical snapshots and event times without changing current menu stock or courier availability.
}
