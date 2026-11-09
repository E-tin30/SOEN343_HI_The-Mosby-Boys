package com.mosbyboys.dishesbecrazy.technical.seed;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class DemoFixtureLoaderTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final DemoFixtureLoader loader = new DemoFixtureLoader(json);

    @Test
    void loadsDemoProfilesWithoutExternalServices() throws Exception {
        DemoFixtures fixtures = loader.load();

        assertEquals(6, fixtures.accounts().size());
        assertEquals(Role.CUSTOMER, fixtures.accounts().getFirst().role());
        assertEquals("demo_customer", fixtures.accounts().getFirst().username());
        var restaurants = fixtures.accounts().stream().filter(a -> a.role() == Role.RESTAURANT).toList();
        assertEquals(Set.of("demo_restaurant", "demo_restaurant_building", "demo_restaurant_busy"),
                restaurants.stream().map(DemoFixtures.Account::username).collect(Collectors.toSet()));
        assertTrue(restaurants.stream().allMatch(a -> a.status().equals("OPEN")));
        assertEquals(3, restaurants.stream().map(a -> List.of(a.latitude(), a.longitude())).distinct().count());
    }
    // Exercises the actual checked-in JSON without provisioning accounts or starting Spring.

    @Test
    void everyDemoRestaurantHasOrderableAndSoldOutMenuItems() throws Exception {
        DemoFixtures fixtures = loader.load();
        var menus = fixtures.menuItems().stream()
                .collect(Collectors.groupingBy(DemoFixtures.MenuSeed::restaurantUsername));
        var owners = fixtures.accounts().stream().filter(a -> a.role() == Role.RESTAURANT)
                .map(DemoFixtures.Account::username).collect(Collectors.toSet());

        assertEquals(12, fixtures.menuItems().size());
        assertEquals(owners, menus.keySet());
        for (var menu : menus.values()) {
            assertEquals(4, menu.size());
            assertEquals(3, menu.stream().filter(item -> item.status() == MenuItemStatus.AVAILABLE
                    && item.stockQuantity() > 0).count());
            assertEquals(1, menu.stream().filter(item -> item.status() == MenuItemStatus.SOLD_OUT
                    && item.stockQuantity() == 0).count());
        }
        assertEquals(new BigDecimal("18.50"), fixtures.menuItems().getFirst().price());
        assertEquals("demo_restaurant", fixtures.menuItems().getFirst().restaurantUsername());
    }
    // Each production menu supports both successful ordering and the sold-out scenario.

    @Test
    void allowsThreeRestaurantAccountsWithDistinctMenus() throws Exception {
        var fixtures = loader.load(new ClassPathResource("demo/fixtures/multiple-profiles.json"),
                new ClassPathResource("demo/fixtures/multiple-menu.json"));

        assertEquals(6, fixtures.accounts().size());
        assertEquals(3, fixtures.accounts().stream().filter(a -> a.role() == Role.RESTAURANT).count());
        assertEquals(Set.of("demo_restaurant", "demo_restaurant_building", "demo_restaurant_busy"),
                fixtures.menuItems().stream().map(DemoFixtures.MenuSeed::restaurantUsername).collect(Collectors.toSet()));
    }
    // Reproduces the three-restaurant requirement that the old unique-role validation rejects.

    @ParameterizedTest
    @EnumSource(Role.class)
    void allowsAnotherDistinctAccountForEachRole(Role role) throws Exception {
        var profiles = profiles();
        long originalCount = profiles.valueStream().filter(a -> a.get("role").asString().equals(role.name())).count();
        var extra = account(profiles, role).deepCopy();
        extra.put("email", "another-" + role.name().toLowerCase(java.util.Locale.ROOT) + ".demo@example.com");
        extra.put("username", "another_" + role.name().toLowerCase(java.util.Locale.ROOT));
        profiles.add(extra);

        var fixtures = load(profiles, menu());

        assertEquals(originalCount + 1, fixtures.accounts().stream().filter(a -> a.role() == role).count());
    }
    // Repeated roles are supported for customers, couriers and operators as well as restaurants.

    @ParameterizedTest
    @EnumSource(Role.class)
    void stillRequiresEveryRole(Role role) throws Exception {
        var profiles = profiles();
        for (int i = profiles.size() - 1; i >= 0; i--) {
            if (profiles.get(i).get("role").asString().equals(role.name())) {
                profiles.remove(i);
            }
        }

        var error = assertThrows(IllegalArgumentException.class, () -> load(profiles, menu()));

        assertTrue(error.getMessage().contains("at least one account of each role"));
    }
    // Relaxing repeated roles must not let a fixture omit a required role entirely.

    @ParameterizedTest
    @ValueSource(strings = {"customer.demo@example.com", "CUSTOMER.DEMO@EXAMPLE.COM"})
    void rejectsDuplicateEmailIdentities(String email) throws Exception {
        var profiles = profiles();
        account(profiles, Role.RESTAURANT).put("email", email);

        assertThrows(IllegalArgumentException.class, () -> load(profiles, menu()));
    }
    // Differently cased spellings cannot represent separate Auth identities in a demo fixture.

    @Test
    void rejectsDuplicateUsernamesAcrossRoles() throws Exception {
        var profiles = profiles();
        account(profiles, Role.OPERATOR).put("username", "demo_customer");

        assertThrows(IllegalArgumentException.class, () -> load(profiles, menu()));
    }
    // Menu ownership and profile lookup depend on unambiguous usernames.

    @ParameterizedTest
    @ValueSource(strings = {"someone@example.com", "two@names.demo@example.com", "space name.demo@example.com"})
    void rejectsAddressesOutsideTheDemoEmailFormat(String email) throws Exception {
        var profiles = profiles();
        account(profiles, Role.CUSTOMER).put("email", email);

        assertThrows(IllegalArgumentException.class, () -> load(profiles, menu()));
    }
    // Invalid demo addresses are rejected before a caller can provision Auth users.

    @ParameterizedTest
    @ValueSource(strings = {"email", "username", "firstName", "lastName", "role"})
    void rejectsMissingIdentityFields(String field) throws Exception {
        var profiles = profiles();
        account(profiles, Role.CUSTOMER).remove(field);

        assertThrows(IllegalArgumentException.class, () -> load(profiles, menu()));
    }
    // Required profile fields cannot become null arguments to Auth or SQL inserts.

    @ParameterizedTest
    @CsvSource({"CUSTOMER,address", "RESTAURANT,restaurantName", "RESTAURANT,status", "COURIER,state"})
    void rejectsMissingRoleSpecificFields(Role role, String field) throws Exception {
        var profiles = profiles();
        account(profiles, role).remove(field);

        assertThrows(IllegalArgumentException.class, () -> load(profiles, menu()));
    }
    // Each profile contains the fields consumed by its own role's existing persistence path.

    @Test
    void rejectsUnknownRestaurantStatus() throws Exception {
        var profiles = profiles();
        account(profiles, Role.RESTAURANT).put("status", "BUSY");

        assertThrows(IllegalArgumentException.class, () -> load(profiles, menu()));
    }
    // Queue congestion is not an additional restaurant status.

    @ParameterizedTest
    @CsvSource({"CUSTOMER,latitude", "CUSTOMER,longitude", "RESTAURANT,latitude",
            "RESTAURANT,longitude", "COURIER,latitude", "COURIER,longitude"})
    void requiresBothCoordinatesForLocatedProfiles(Role role, String field) throws Exception {
        var profiles = profiles();
        account(profiles, role).remove(field);

        assertThrows(IllegalArgumentException.class, () -> load(profiles, menu()));
    }
    // Customer/courier columns are NOT NULL, and all three located roles must map meaningfully.

    @ParameterizedTest
    @CsvSource({"latitude,91", "latitude,-91", "longitude,181", "longitude,-181",
            "latitude,NaN", "longitude,Infinity"})
    void rejectsOutOfRangeOrNonFiniteCoordinates(String field, double value) throws Exception {
        var profiles = profiles();
        account(profiles, Role.CUSTOMER).put(field, value);

        assertThrows(IllegalArgumentException.class, () -> load(profiles, menu()));
    }
    // NaN/infinite coordinates are invalid even if JSON coercion produces a Double.

    @Test
    void acceptsCoordinateBoundariesAndAnOperatorWithoutLocation() throws Exception {
        var profiles = profiles();
        account(profiles, Role.CUSTOMER).put("latitude", -90).put("longitude", 180);
        account(profiles, Role.RESTAURANT).put("latitude", 90).put("longitude", -180);

        assertEquals(profiles.size(), load(profiles, menu()).accounts().size());
    }
    // Boundary coordinates are legal; operator fixtures do not require unused location fields.

    @ParameterizedTest
    @ValueSource(strings = {"missing_restaurant", "demo_customer", "demo_courier", "demo_operator"})
    void menuOwnershipRequiresAnExistingRestaurantProfile(String username) throws Exception {
        var menu = menu();
        menu.get(0).asObject().put("restaurantUsername", username);

        assertThrows(IllegalArgumentException.class, () -> load(profiles(), menu));
    }
    // A username must resolve to a fixture restaurant, not just any account or database row.

    @Test
    void rejectsDuplicateMenuIds() throws Exception {
        var menu = menu();
        menu.get(1).asObject().put("id", menu.get(0).get("id").asString());

        assertThrows(IllegalArgumentException.class, () -> load(profiles(), menu));
    }
    // Conflicting fixture IDs cannot silently disappear through ON CONFLICT DO NOTHING.

    @ParameterizedTest
    @ValueSource(strings = {"id", "restaurantUsername", "name", "price", "status", "description"})
    void rejectsMissingMenuFields(String field) throws Exception {
        var menu = menu();
        menu.get(0).asObject().remove(field);

        assertThrows(IllegalArgumentException.class, () -> load(profiles(), menu));
    }
    // Required menu values fail validation before the menu transaction can start.

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01"})
    void rejectsNonPositivePrices(String price) throws Exception {
        var menu = menu();
        menu.get(0).asObject().put("price", new BigDecimal(price));

        assertThrows(IllegalArgumentException.class, () -> load(profiles(), menu));
    }
    // Fixture prices retain the existing strictly positive pricing rule.

    @Test
    void rejectsNegativeStock() throws Exception {
        var menu = menu();
        menu.get(0).asObject().put("stockQuantity", -1);

        assertThrows(IllegalArgumentException.class, () -> load(profiles(), menu));
    }

    @Test
    void rejectsAnAvailableItemWithNoStock() throws Exception {
        var menu = menu();
        menu.get(0).asObject().put("stockQuantity", 0);

        assertThrows(IllegalArgumentException.class, () -> load(profiles(), menu));
    }
    // Zero stock is valid for the existing SOLD_OUT fixture, but not for AVAILABLE items.

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "[null]"})
    void rejectsMissingOrNullProfileRecords(String document) {
        assertThrows(IllegalArgumentException.class,
                () -> loader.load(resource(document), new ClassPathResource("demo/menu.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "[null]"})
    void rejectsMissingOrNullMenuRecords(String document) {
        assertThrows(IllegalArgumentException.class,
                () -> loader.load(new ClassPathResource("demo/profiles.json"), resource(document)));
    }
    // Null arrays/records must be validation failures rather than accidental null dereferences.

    @Test
    void rejectsMalformedJson() {
        assertThrows(JacksonException.class,
                () -> loader.load(resource("[broken"), new ClassPathResource("demo/menu.json")));
        assertThrows(JacksonException.class,
                () -> loader.load(new ClassPathResource("demo/profiles.json"), resource("[broken")));
    }
    // Neither file can supply a partial fixture snapshot after a parsing failure.

    @Test
    void rejectsAnEmptyMenuBeforeReturningFixtures() {
        var profiles = new ClassPathResource("demo/profiles.json");
        var menu = resource("[]");

        var error = assertThrows(IllegalArgumentException.class, () -> loader.load(profiles, menu));

        assertEquals("Menu seed configuration has no items.", error.getMessage());
    }
    // A caller cannot begin profile seeding with a menu that failed preflight validation.

    private ArrayNode profiles() throws Exception {
        return readArray("demo/profiles.json");
    }

    private ArrayNode menu() throws Exception {
        return readArray("demo/menu.json");
    }

    private ArrayNode readArray(String path) throws Exception {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return json.readTree(input).asArray();
        }
    }
    // Mutations begin from the real valid fixtures, so each test isolates its invalid field.

    private ObjectNode account(ArrayNode profiles, Role role) {
        for (JsonNode profile : profiles) {
            if (profile.get("role").asString().equals(role.name())) {
                return profile.asObject();
            }
        }
        throw new IllegalArgumentException("Test fixture has no " + role);
    }

    private DemoFixtures load(ArrayNode profiles, ArrayNode menu) throws Exception {
        return loader.load(new ByteArrayResource(json.writeValueAsBytes(profiles)),
                new ByteArrayResource(json.writeValueAsBytes(menu)));
    }

    private ByteArrayResource resource(String document) {
        return new ByteArrayResource(document.getBytes(StandardCharsets.UTF_8));
    }
    // Exercises the public resource-loading API using in-memory JSON rather than external services.
}
