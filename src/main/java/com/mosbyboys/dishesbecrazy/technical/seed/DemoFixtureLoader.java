package com.mosbyboys.dishesbecrazy.technical.seed;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.Account;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.MenuSeed;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoFixtures.OrderSeed;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class DemoFixtureLoader {
    private final JsonMapper json;

    public DemoFixtures load() throws IOException {
        return load(new ClassPathResource("demo/profiles.json"), new ClassPathResource("demo/menu.json"),
                new ClassPathResource("demo/orders.json"));
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
        return new DemoFixtures(Arrays.asList(accounts), Arrays.asList(items), List.of());
    }
    // Catalogue-only loading keeps custom profile/menu fixtures independent of the production orders.

    public DemoFixtures load(Resource profiles, Resource menu, Resource orders) throws IOException {
        DemoFixtures catalogue = load(profiles, menu);
        OrderSeed[] entries;
        try (var input = orders.getInputStream()) {
            entries = json.readValue(input, OrderSeed[].class);
        }
        new DemoOrderFixtureValidator(catalogue).validate(entries);
        return new DemoFixtures(catalogue.accounts(), catalogue.menuItems(), Arrays.asList(entries));
    }
    // Default seeding validates the complete baseline before either runner can contact external services.

    private void validateAccounts(Account[] accounts) {
        Assert.isTrue(accounts != null && accounts.length > 0, "Demo profile configuration has no accounts.");
        // Rejects an absent dataset before checking role coverage or individual records.

        var roles = EnumSet.noneOf(Role.class);
        var emails = new HashSet<String>();
        var usernames = new HashSet<String>();
        for (Account account : accounts) {
            Assert.isTrue(account != null && account.role() != null, "Every demo account requires a role.");
            String email = account.email();
            Assert.isTrue(email != null && email.matches("[^\\s@]+\\.demo@example\\.com") && emails.add(email),
                    "Demo accounts require unique emails ending in .demo@example.com.");
            Assert.hasText(account.username(), "Demo username is required.");
            Assert.isTrue(usernames.add(account.username()), "Demo accounts require unique usernames.");
            Assert.hasText(account.firstName(), "Demo first name is required.");
            Assert.hasText(account.lastName(), "Demo last name is required.");
            roles.add(account.role());
            // Identities stay unique while several accounts may share the same role.

            if (account.role() != Role.OPERATOR) {
                validateCoordinates(account);
            }
            switch (account.role()) {
                case CUSTOMER -> Assert.hasText(account.address(), "Customer address is required.");
                case RESTAURANT -> {
                    Assert.hasText(account.restaurantName(), "Restaurant name is required.");
                    Assert.hasText(account.status(), "Restaurant status is required.");
                    Assert.isTrue(Arrays.stream(RestaurantStatus.values())
                                    .anyMatch(status -> status.name().equals(account.status())),
                            "Restaurant status must be OPEN, PAUSED or CLOSED.");
                }
                case COURIER -> {
                    Assert.hasText(account.state(), "Courier state is required.");
                    Assert.isTrue(Arrays.stream(CourierState.values())
                                    .anyMatch(state -> state.name().equals(account.state())),
                            "Courier state must be OFFLINE, IDLE, ASSIGNED or CARRYING.");
                }
                case OPERATOR -> { }
            }
            // Checks fields consumed by each existing role-specific profile insert.
        }
        Assert.isTrue(roles.size() == Role.values().length,
                "Seed configuration requires at least one account of each role.");
    }
    // Every role must remain represented in a multi-account demonstration.

    private void validateCoordinates(Account account) {
        Assert.isTrue(account.latitude() != null && Double.isFinite(account.latitude())
                        && account.latitude() >= -90 && account.latitude() <= 90
                        && account.longitude() != null && Double.isFinite(account.longitude())
                        && account.longitude() >= -180 && account.longitude() <= 180,
                "Demo " + account.role() + " profiles require finite latitude (-90..90) and longitude (-180..180).");
    }
    // Prevents missing locations and invalid map coordinates from reaching profile persistence.

    private void validateMenu(MenuSeed[] items, Account[] accounts) {
        Assert.isTrue(items != null && items.length > 0, "Menu seed configuration has no items.");
        var restaurants = Arrays.stream(accounts)
                .filter(account -> account.role() == Role.RESTAURANT)
                .map(Account::username)
                .collect(Collectors.toSet());
        // Resolves menu ownership against the validated fixture instead of whichever database rows happen to exist.

        var ids = new HashSet<UUID>();
        for (MenuSeed item : items) {
            Assert.isTrue(item != null && item.id() != null && ids.add(item.id()), "Menu items require a unique id.");
            Assert.isTrue(item.restaurantUsername() != null && restaurants.contains(item.restaurantUsername()),
                    "Menu item " + item.id() + " must reference a restaurant account in the demo profiles.");
            Assert.isTrue(item.name() != null && !item.name().isBlank(),
                    "Menu item " + item.id() + " requires a name.");
            Assert.isTrue(item.price() != null && item.price().signum() > 0,
                    "Menu item " + item.name() + " requires a price above 0.");
            Assert.isTrue(item.stockQuantity() >= 0, "Menu item " + item.name() + " cannot have negative stock.");
            Assert.isTrue(item.status() != null, "Menu item " + item.name() + " requires a status.");
            Assert.isTrue(item.status() != MenuItemStatus.AVAILABLE || item.stockQuantity() != 0,
                    "Menu item " + item.name() + " is AVAILABLE with no stock.");
            Assert.isTrue(item.description() != null,
                    "Menu item " + item.name() + " requires a description; use an empty string when absent.");
            // Enforces the menu's ordering rules and its existing NOT NULL description column.
        }
    }
    // Validates menu data without depending on SQL or Auth services.
}
