package com.mosbyboys.dishesbecrazy.technical.seed;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.Account;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.MenuSeed;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class DemoFixtureLoader {
    private final JsonMapper json;

    public DemoFixtures load() throws IOException {
        return load(new ClassPathResource("demo/profiles.json"), new ClassPathResource("demo/menu.json"));
    }
    // Uses the same fixture sources for startup seeding and future restoration.

    public DemoFixtures load(Resource profiles, Resource menu) throws IOException {
        Account[] accounts;
        MenuSeed[] items;
        try (var input = profiles.getInputStream()) {
            accounts = json.readValue(input, Account[].class);
        }
        try (var input = menu.getInputStream()) {
            items = json.readValue(input, MenuSeed[].class);
        }

        validateAccounts(accounts);
        validateMenu(items);
        return new DemoFixtures(Arrays.asList(accounts), Arrays.asList(items));
    }
    // Validates both files before a caller can start Auth provisioning or database writes.

    private void validateAccounts(Account[] accounts) {
        var roles = EnumSet.noneOf(Role.class);
        var emails = new HashSet<String>();
        for (Account account : accounts) {
            if (account.email() == null || !account.email().endsWith(".demo@example.com")
                    || !emails.add(account.email()) || account.role() == null || !roles.add(account.role())) {
                throw new IllegalArgumentException("Seed configuration requires unique demo emails and roles.");
            }
            if (account.role() == Role.RESTAURANT && (account.latitude() == null || account.longitude() == null)) {
                throw new IllegalArgumentException("Restaurant profiles require latitude and longitude.");
            }
        }
        if (roles.size() != Role.values().length) {
            throw new IllegalArgumentException("Seed configuration requires one account of each role.");
        }
    }
    // Retains the existing account validation during the loading extraction.

    private void validateMenu(MenuSeed[] items) {
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
    // Keeps the existing menu rules independent of SQL and Auth services.
}
