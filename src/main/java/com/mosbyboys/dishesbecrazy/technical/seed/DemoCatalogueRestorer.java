package com.mosbyboys.dishesbecrazy.technical.seed;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.Account;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.MenuSeed;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class DemoCatalogueRestorer {
    private final JdbcTemplate jdbc;

    Map<String, UUID> resolveUsers(DemoFixtures baseline) {
        var users = new HashMap<String, UUID>();
        for (var account : baseline.accounts()) {
            var ids = jdbc.queryForList("SELECT id FROM auth.users WHERE lower(email) = lower(?)",
                    UUID.class, account.email());
            Assert.state(ids.size() == 1, "Run demo-seed first: no single Auth account matches " + account.email());
            UUID id = ids.getFirst();
            users.put(account.username(), id);
        }
        return users;
    }
    // Email identifies existing Auth accounts even after a public profile is renamed or deleted; Auth itself is unchanged.

    void restore(DemoFixtures baseline, Map<String, UUID> users) {
        for (var account : baseline.accounts()) {
            restoreProfile(account, users.get(account.username()));
        }
        for (var item : baseline.menuItems()) {
            restoreMenu(item, users.get(item.restaurantUsername()));
        }
    }
    // Restore parents before menus and orders; existing profile and menu creation timestamps stay intact.

    private void restoreProfile(Account account, UUID id) {
        int updated = jdbc.update("""
                UPDATE public."User" SET first_name = ?, last_name = ?, username = ?, role = ? WHERE id = ?
                """, account.firstName(), account.lastName(), account.username(), account.role().name(), id);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO public."User" (id, first_name, last_name, username, role) VALUES (?, ?, ?, ?, ?)
                    """, id, account.firstName(), account.lastName(), account.username(), account.role().name());
        }
        switch (account.role()) {
            case CUSTOMER -> {
                updated = jdbc.update("""
                        UPDATE public."Customer" SET address = ?, latitude = ?, longitude = ? WHERE user_id = ?
                        """, account.address(), account.latitude(), account.longitude(), id);
                if (updated == 0) jdbc.update("""
                        INSERT INTO public."Customer" (user_id, address, latitude, longitude) VALUES (?, ?, ?, ?)
                        """, id, account.address(), account.latitude(), account.longitude());
            }
            case RESTAURANT -> {
                updated = jdbc.update("""
                        UPDATE public."Restaurant" SET name = ?, status = ?, latitude = ?, longitude = ? WHERE user_id = ?
                        """, account.restaurantName(), account.status(), account.latitude(), account.longitude(), id);
                if (updated == 0) jdbc.update("""
                        INSERT INTO public."Restaurant" (user_id, name, status, latitude, longitude) VALUES (?, ?, ?, ?, ?)
                        """, id, account.restaurantName(), account.status(), account.latitude(), account.longitude());
            }
            case COURIER -> {
                updated = jdbc.update("""
                        UPDATE public."Courier" SET state = ?, latitude = ?, longitude = ? WHERE user_id = ?
                        """, account.state(), account.latitude(), account.longitude(), id);
                if (updated == 0) jdbc.update("""
                        INSERT INTO public."Courier" (user_id, state, latitude, longitude) VALUES (?, ?, ?, ?)
                        """, id, account.state(), account.latitude(), account.longitude());
            }
            case OPERATOR -> { /* The shared User row is the entire operator profile. */ }
        }
    }
    // Updating in place preserves identities and sessions; insert missing public rows under those same Auth IDs.

    private void restoreMenu(MenuSeed item, UUID restaurantId) {
        int updated = jdbc.update("""
                UPDATE public."MenuItem" SET restaurant_id = ?, name = ?, price = ?, stock_quantity = ?,
                    status = ?, description = ?, image_url = '' WHERE id = ?
                """, restaurantId, item.name(), item.price(), item.stockQuantity(), item.status().name(), item.description(), item.id());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO public."MenuItem" (id, restaurant_id, name, price, stock_quantity, status, description, image_url)
                    VALUES (?, ?, ?, ?, ?, ?, ?, '')
                    """, item.id(), restaurantId, item.name(), item.price(), item.stockQuantity(), item.status().name(), item.description());
        }
    }
    // Fixtures define stock and availability directly; restoring historical orders must not consume that stock again.
}
