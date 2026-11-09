package com.mosbyboys.dishesbecrazy.technical.seed;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
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
        validateMenu(items, accounts);
        return new DemoFixtures(Arrays.asList(accounts), Arrays.asList(items));
    }
    // Validates both files before a caller can start Auth provisioning or database writes.

    private void validateAccounts(Account[] accounts) {
        if (accounts == null || accounts.length == 0) {
            throw new IllegalArgumentException("Demo profile configuration has no accounts.");
        }
        // Rejects an absent dataset before checking role coverage or individual records.

        var roles = EnumSet.noneOf(Role.class);
        var emails = new HashSet<String>();
        var usernames = new HashSet<String>();
        for (Account account : accounts) {
            if (account == null || account.role() == null) {
                throw new IllegalArgumentException("Every demo account requires a role.");
            }
            if (account.email() == null
                    || !account.email().toLowerCase(Locale.ROOT).matches("[^\\s@]+\\.demo@example\\.com")
                    || !emails.add(account.email().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Demo accounts require unique emails ending in .demo@example.com.");
            }
            requireText(account.username(), "Demo username");
            if (!usernames.add(account.username())) {
                throw new IllegalArgumentException("Demo accounts require unique usernames.");
            }
            requireText(account.firstName(), "Demo first name");
            requireText(account.lastName(), "Demo last name");
            roles.add(account.role());
            // Identities stay unique while several accounts may share the same role.

            if (account.role() != Role.OPERATOR) {
                validateCoordinates(account);
            }
            switch (account.role()) {
                case CUSTOMER -> requireText(account.address(), "Customer address");
                case RESTAURANT -> {
                    requireText(account.restaurantName(), "Restaurant name");
                    requireText(account.status(), "Restaurant status");
                    try {
                        RestaurantStatus.valueOf(account.status());
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException("Restaurant status must be OPEN, PAUSED or CLOSED.");
                    }
                }
                case COURIER -> requireText(account.state(), "Courier state");
                case OPERATOR -> { }
            }
            // Checks fields consumed by each existing role-specific profile insert.
        }
        if (roles.size() != Role.values().length) {
            throw new IllegalArgumentException("Seed configuration requires at least one account of each role.");
        }
    }
    // Every role must remain represented in a multi-account demonstration.

    private void validateCoordinates(Account account) {
        if (account.latitude() == null || !Double.isFinite(account.latitude())
                || account.latitude() < -90 || account.latitude() > 90
                || account.longitude() == null || !Double.isFinite(account.longitude())
                || account.longitude() < -180 || account.longitude() > 180) {
            throw new IllegalArgumentException("Demo " + account.role()
                    + " profiles require finite latitude (-90..90) and longitude (-180..180).");
        }
    }
    // Prevents missing locations and invalid map coordinates from reaching profile persistence.

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
    }
    // Gives fixture authors a field-specific error before provisioning or persistence begins.

    private void validateMenu(MenuSeed[] items, Account[] accounts) {
        if (items == null || items.length == 0) {
            throw new IllegalArgumentException("Menu seed configuration has no items.");
        }
        var restaurants = Arrays.stream(accounts)
                .filter(account -> account.role() == Role.RESTAURANT)
                .map(Account::username)
                .collect(Collectors.toSet());
        // Resolves menu ownership against the validated fixture instead of whichever database rows happen to exist.

        var ids = new HashSet<UUID>();
        for (MenuSeed item : items) {
            if (item == null || item.id() == null || !ids.add(item.id())) {
                throw new IllegalArgumentException("Menu items require a unique id.");
            }
            if (item.restaurantUsername() == null || !restaurants.contains(item.restaurantUsername())) {
                throw new IllegalArgumentException("Menu item " + item.id()
                        + " must reference a restaurant account in the demo profiles.");
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
            if (item.description() == null) {
                throw new IllegalArgumentException("Menu item " + item.name()
                        + " requires a description; use an empty string when absent.");
            }
            // Enforces the menu's ordering rules and its existing NOT NULL description column.
        }
    }
    // Validates menu data without depending on SQL or Auth services.
}
