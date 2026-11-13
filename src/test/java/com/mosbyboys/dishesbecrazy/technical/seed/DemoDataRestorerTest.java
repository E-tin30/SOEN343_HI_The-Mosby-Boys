package com.mosbyboys.dishesbecrazy.technical.seed;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import com.mosbyboys.dishesbecrazy.domain.reg.Role;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoDataRestorerTest {
    private JdbcTemplate jdbc;
    private DemoFixtureLoader loader;
    private DemoFixtures baseline;
    private DemoDataRestorer restorer;
    private final Map<String, UUID> users = new LinkedHashMap<>();
    private UUID outsideCustomer;
    private UUID outsideRestaurant;
    private UUID outsideMenu;

    @BeforeEach
    void prepareDatabase() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("demo/restoration-schema.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        loader = spy(new DemoFixtureLoader(JsonMapper.builder().build()));
        baseline = loader.load();
        restorer = new DemoDataRestorer(jdbc, new TransactionTemplate(new DataSourceTransactionManager(source)),
                loader, new DemoCatalogueRestorer(jdbc), new DemoOrderStore(jdbc));
        for (var account : baseline.accounts()) {
            UUID id = UUID.randomUUID();
            users.put(account.username(), id);
            jdbc.update("INSERT INTO auth.users (id, email) VALUES (?, ?)", id, account.email());
        }
    }
    // Only Auth identities exist initially; no shared database, Auth API or application server is contacted.

    @Test
    void restoresTheFixtureBaselineUnderExistingAuthIdentities() throws Exception {
        var auth = jdbc.queryForList("SELECT * FROM auth.users ORDER BY id");

        restorer.restore();

        assertBaseline();
        assertEquals(auth, jdbc.queryForList("SELECT * FROM auth.users ORDER BY id"));
    }
    // Fixture Auth identities retain their IDs and login data while application records return to the starting point.

    @Test
    void rewindsChangedProfilesStockOrdersAndHistory() throws Exception {
        restorer.restore();
        var expected = snapshot();
        jdbc.update("UPDATE public.\"User\" SET first_name = 'Changed', last_name = 'Changed', username = 'changed_' || username");
        jdbc.update("UPDATE public.\"Customer\" SET address = 'Other address', latitude = 0, longitude = 0");
        jdbc.update("UPDATE public.\"Restaurant\" SET name = 'Changed', status = 'PAUSED', latitude = 0, longitude = 0");
        jdbc.update("UPDATE public.\"Courier\" SET state = 'CARRYING', latitude = 0, longitude = 0");
        jdbc.update("""
                UPDATE public."MenuItem" SET name = 'Changed', price = 99, stock_quantity = 0,
                    status = 'SOLD_OUT', description = 'Changed', image_url = 'changed.png'
                """);
        jdbc.update("""
                UPDATE public."Order" SET state = 'DeliveredState', courier_id = ?, subtotal = 99,
                    rejection_reason = 'Changed', prepared_at = CURRENT_TIMESTAMP, ready_at = CURRENT_TIMESTAMP,
                    delivered_at = CURRENT_TIMESTAMP, created_at = CURRENT_TIMESTAMP
                """, users.get("demo_courier"));
        jdbc.update("UPDATE public.\"OrderLineItem\" SET quantity = 2, unit_price = 99, menu_item_name = 'Changed'");
        jdbc.update("UPDATE public.\"Event\" SET action = 'Changed', new_state = 'Changed', created_at = CURRENT_TIMESTAMP");

        restorer.restore();

        assertEquals(expected, snapshot());
    }
    // Restore the complete configured data, including stored event times and cleared workflow timestamps.

    @ParameterizedTest
    @EnumSource(Role.class)
    void recreatesDeletedProfilesAndTheirCascadedFixtureRows(Role role) throws Exception {
        restorer.restore();
        var account = baseline.accounts().stream().filter(entry -> entry.role() == role).findFirst().orElseThrow();
        jdbc.update("DELETE FROM public.\"User\" WHERE id = ?", users.get(account.username()));

        restorer.restore();

        assertBaseline();
        assertEquals(users.get(account.username()), jdbc.queryForObject(
                "SELECT id FROM public.\"User\" WHERE username = ?", UUID.class, account.username()));
    }

    @Test
    void recreatesDeletedMenuAndRoleSpecificRows() throws Exception {
        restorer.restore();
        jdbc.update("DELETE FROM public.\"MenuItem\" WHERE id = ?", baseline.orders().getFirst().lines().getFirst().menuItemId());
        jdbc.update("DELETE FROM public.\"Customer\" WHERE user_id = ?", users.get("demo_customer"));
        jdbc.update("DELETE FROM public.\"Courier\" WHERE user_id = ?", users.get("demo_courier"));

        restorer.restore();

        assertBaseline();
    }

    @Test
    void removesEveryonesExtraProfilesMenusOrdersEventsAndBills() throws Exception {
        restorer.restore();
        var expected = snapshot();
        UUID outsideOrder = unrelatedOrder();
        for (String state : List.of("IDLE", "ASSIGNED", "CARRYING")) {
            UUID courier = addCourier("outside_" + state.toLowerCase(), state);
            giveToCourier(addOrder(outsideCustomer, outsideRestaurant, outsideMenu), "ReadyState", courier);
        }
        addUser("outside_operator", "OPERATOR");
        UUID extraMenu = addMenu(users.get("demo_restaurant"));
        addOrder(outsideCustomer, outsideRestaurant, extraMenu);
        addOrder(users.get("demo_customer"), outsideRestaurant, outsideMenu);
        addOrder(outsideCustomer, users.get("demo_restaurant"), baseline.menuItems().getFirst().id());
        jdbc.update("INSERT INTO public.\"Bill\" (id, user_id) VALUES (?, ?)", UUID.randomUUID(), users.get("demo_customer"));

        restorer.restore();

        assertEquals(expected, snapshot());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM public.\"Order\" WHERE id = ?", Integer.class, outsideOrder));
        assertBaseline();
    }
    // Every workflow record is reset regardless of who created it; only fixture Auth accounts, profiles and menus remain.

    @Test
    void removesOrphanAuthAccountsSoTheirEmailCanBeRegisteredAgain() throws Exception {
        restorer.restore();
        var expected = snapshot();
        String email = "register.again@example.com";
        UUID orphan = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id, email) VALUES (?, ?)", orphan, email);

        restorer.restore();

        assertEquals(expected, snapshot());
        UUID registered = addUser("register.again", "CUSTOMER");
        assertNotEquals(orphan, registered);
        assertEquals(registered, jdbc.queryForObject("SELECT id FROM auth.users WHERE email = ?", UUID.class, email));

        restorer.restore();
        assertEquals(expected, snapshot());
    }
    // Exercise the unique-email constraint locally; this does not call the Supabase signup API.

    @Test
    void authDeletionFailureRollsBackApplicationRestoration() throws Exception {
        restorer.restore();
        UUID outside = addUser("outside_operator", "OPERATOR");
        jdbc.execute("CREATE TABLE auth.deletion_blocker (user_id uuid REFERENCES auth.users(id))");
        jdbc.update("INSERT INTO auth.deletion_blocker (user_id) VALUES (?)", outside);
        jdbc.update("UPDATE public.\"Restaurant\" SET status = 'PAUSED'");
        var expected = snapshot();

        assertThrows(DataAccessException.class, restorer::restore);

        assertEquals(expected, snapshot());
    }
    // A restricted Auth reference must fail the entire Reset instead of committing deleted application profiles.

    @ParameterizedTest
    @CsvSource({"ReadyState, IDLE", "ReadyState, ASSIGNED", "PickedUpState, CARRYING"})
    void resetsActiveDeliveriesRegardlessOfTheirCustomerAndRestaurant(String orderState, String courierState) throws Exception {
        restorer.restore();
        var expected = snapshot();
        UUID order = unrelatedOrder();
        UUID courier = users.get("demo_courier");
        giveToCourier(order, orderState, courier);
        jdbc.update("UPDATE public.\"Courier\" SET state = ?, latitude = 0, longitude = 0 WHERE user_id = ?", courierState, courier);

        restorer.restore();

        assertEquals(expected, snapshot());
    }
    // Pending offers, accepted deliveries and carrying all return to the same initial state without an ownership refusal.

    @ParameterizedTest
    @ValueSource(strings = {"DeliveredState", "RefundedState", "CancelledState", "RejectedState"})
    void clearsOtherAccountsTerminalHistory(String orderState) throws Exception {
        restorer.restore();
        var expected = snapshot();
        UUID order = unrelatedOrder();
        giveToCourier(order, orderState, users.get("demo_courier"));

        restorer.restore();

        assertEquals(expected, snapshot());
    }

    @Test
    void restoresFixtureOwnershipEvenWhenIdsHaveBeenMovedToOtherAccounts() throws Exception {
        restorer.restore();
        var expected = snapshot();
        UUID outsideOrder = unrelatedOrder();
        jdbc.update("UPDATE public.\"MenuItem\" SET restaurant_id = ? WHERE id = ?",
                outsideRestaurant, baseline.menuItems().getFirst().id());
        jdbc.update("UPDATE public.\"Order\" SET user_id = ?, restaurant_id = ? WHERE id = ?",
                outsideCustomer, outsideRestaurant, baseline.orders().getFirst().id());
        jdbc.update("UPDATE public.\"Event\" SET order_id = ? WHERE id = ?",
                outsideOrder, baseline.orders().getLast().events().getLast().id());

        restorer.restore();

        assertEquals(expected, snapshot());
    }
    // Global restoration repairs fixture IDs instead of preserving another account's reassignment of those records.

    @Test
    void repairsAChangedFixtureRoleAndRemovesItsExtraSubtype() throws Exception {
        restorer.restore();
        var expected = snapshot();
        UUID customer = users.get("demo_customer");
        jdbc.update("UPDATE public.\"User\" SET role = 'COURIER' WHERE id = ?", customer);
        jdbc.update("INSERT INTO public.\"Courier\" (user_id, state, latitude, longitude) VALUES (?, 'IDLE', 0, 0)", customer);

        restorer.restore();

        assertEquals(expected, snapshot());
    }

    @Test
    void repeatedRestorationLeavesTheSameBaseline() throws Exception {
        restorer.restore();
        var expected = snapshot();

        restorer.restore();
        restorer.restore();

        assertEquals(expected, snapshot());
        assertBaseline();
    }

    @Test
    void missingAuthAccountFailsWithoutChangingTheDatabase() throws Exception {
        restorer.restore();
        jdbc.update("DELETE FROM auth.users WHERE id = ?", users.get("demo_operator"));
        var expected = snapshot();

        assertThrows(IllegalStateException.class, restorer::restore);

        assertEquals(expected, snapshot());
    }

    @Test
    void unreadableFixturesFailBeforeChangingTheDatabase() throws Exception {
        restorer.restore();
        var expected = snapshot();
        doThrow(new IOException("Fixture unavailable")).when(loader).load();

        assertThrows(IOException.class, restorer::restore);

        assertEquals(expected, snapshot());
    }

    @Test
    void lateFixtureInsertFailureRollsBackTheWholeGlobalReset() throws Exception {
        restorer.restore();
        unrelatedOrder();
        var last = baseline.orders().getLast().events().getLast();
        jdbc.update("DELETE FROM public.\"Event\" WHERE id = ?", last.id());
        jdbc.execute("ALTER TABLE public.\"Event\" ADD CONSTRAINT reject_last_fixture_event CHECK (id <> '" + last.id() + "')");
        jdbc.update("UPDATE public.\"Restaurant\" SET status = 'PAUSED' WHERE user_id = ?", users.get("demo_restaurant"));
        jdbc.update("UPDATE public.\"MenuItem\" SET stock_quantity = 0 WHERE id = ?", baseline.menuItems().getFirst().id());
        var expected = snapshot();

        assertThrows(DataAccessException.class, restorer::restore);

        assertEquals(expected, snapshot());
    }
    // A real H2 constraint fails at the final history insert; all earlier global deletes and profile/menu changes must roll back.

    private void assertBaseline() {
        assertEquals(users.size(), jdbc.queryForObject("SELECT COUNT(*) FROM auth.users", Integer.class));
        assertEquals(users.values().stream().sorted().toList(),
                jdbc.queryForList("SELECT id FROM auth.users", UUID.class).stream().sorted().toList());
        assertEquals(6, count("User"));
        assertEquals(1, count("Customer"));
        assertEquals(1, count("Courier"));
        assertEquals(3, count("Restaurant"));
        assertEquals(12, count("MenuItem"));
        assertEquals(13, count("Order"));
        assertEquals(13, count("OrderLineItem"));
        assertEquals(35, count("Event"));
        assertEquals(0, count("Bill"));
        var refunded = baseline.orders().stream().filter(order -> order.state().equals("RefundedState"))
                .findFirst().orElseThrow();
        assertEquals("RefundedState", jdbc.queryForObject("SELECT state FROM public.\"Order\" WHERE id = ?",
                String.class, refunded.id()));
        assertEquals(users.get("demo_operator"), jdbc.queryForObject(
                "SELECT performed_by FROM public.\"Event\" WHERE order_id = ? AND action = 'REFUND'",
                UUID.class, refunded.id()));
        for (var account : baseline.accounts()) {
            var user = jdbc.queryForMap("SELECT * FROM public.\"User\" WHERE id = ?", users.get(account.username()));
            assertEquals(account.firstName(), user.get("first_name"));
            assertEquals(account.lastName(), user.get("last_name"));
            assertEquals(account.username(), user.get("username"));
            assertEquals(account.role().name(), user.get("role"));
            if (account.role() == Role.OPERATOR) continue;
            String table = switch (account.role()) {
                case CUSTOMER -> "Customer";
                case COURIER -> "Courier";
                case RESTAURANT -> "Restaurant";
                case OPERATOR -> throw new IllegalStateException("Operators have no role-specific table");
            };
            var profile = jdbc.queryForMap("SELECT * FROM public.\"" + table + "\" WHERE user_id = ?", users.get(account.username()));
            assertEquals(account.latitude(), profile.get("latitude"));
            assertEquals(account.longitude(), profile.get("longitude"));
            switch (account.role()) {
                case CUSTOMER -> assertEquals(account.address(), profile.get("address"));
                case COURIER -> assertEquals(account.state(), profile.get("state"));
                case RESTAURANT -> {
                    assertEquals(account.restaurantName(), profile.get("name"));
                    assertEquals(account.status(), profile.get("status"));
                }
                case OPERATOR -> { }
            }
        }
        for (var menu : baseline.menuItems()) {
            var row = jdbc.queryForMap("SELECT * FROM public.\"MenuItem\" WHERE id = ?", menu.id());
            assertEquals(users.get(menu.restaurantUsername()), row.get("restaurant_id"));
            assertEquals(menu.name(), row.get("name"));
            assertEquals(0, menu.price().compareTo((BigDecimal) row.get("price")));
            assertEquals(menu.stockQuantity(), row.get("stock_quantity"));
            assertEquals(menu.status().name(), row.get("status"));
            assertEquals(menu.description(), row.get("description"));
            assertEquals("", row.get("image_url"));
        }
        var preparationCounts = Map.of("demo_restaurant", 0, "demo_restaurant_building", 3, "demo_restaurant_busy", 6);
        for (var entry : preparationCounts.entrySet()) {
            assertEquals(entry.getValue(), jdbc.queryForObject("""
                    SELECT COUNT(*) FROM public."Order" WHERE restaurant_id = ? AND state = 'AcceptedState'
                    """, Integer.class, users.get(entry.getKey())));
        }
        assertEquals("OFFLINE", jdbc.queryForObject("SELECT state FROM public.\"Courier\"", String.class));
    }

    private UUID unrelatedOrder() {
        outsideCustomer = addUser("outside_customer", "CUSTOMER");
        jdbc.update("INSERT INTO public.\"Customer\" (user_id, address, latitude, longitude) VALUES (?, 'Outside', 40, -70)", outsideCustomer);
        outsideRestaurant = addUser("outside_restaurant", "RESTAURANT");
        jdbc.update("INSERT INTO public.\"Restaurant\" (user_id, name, status) VALUES (?, 'Outside kitchen', 'PAUSED')", outsideRestaurant);
        outsideMenu = addMenu(outsideRestaurant);
        UUID id = addOrder(outsideCustomer, outsideRestaurant, outsideMenu);
        jdbc.update("INSERT INTO public.\"Bill\" (id, order_id, user_id, restaurant_id, total) VALUES (?, ?, ?, ?, 25)",
                UUID.randomUUID(), id, outsideCustomer, outsideRestaurant);
        return id;
    }

    private UUID addUser(String username, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth.users (id, email) VALUES (?, ?)", id, username + "@example.com");
        jdbc.update("INSERT INTO public.\"User\" (id, username, role) VALUES (?, ?, ?)", id, username, role);
        return id;
    }

    private UUID addCourier(String username, String state) {
        UUID id = addUser(username, "COURIER");
        jdbc.update("INSERT INTO public.\"Courier\" (user_id, state, latitude, longitude) VALUES (?, ?, 45.5, -73.6)", id, state);
        return id;
    }

    private void giveToCourier(UUID order, String state, UUID courier) {
        jdbc.update("UPDATE public.\"Order\" SET state = ?, courier_id = ? WHERE id = ?", state, courier, order);
    }

    private UUID addMenu(UUID restaurant) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public."MenuItem" (id, restaurant_id, name, price, stock_quantity, status)
                VALUES (?, ?, 'Extra meal', 20, 5, 'AVAILABLE')
                """, id, restaurant);
        return id;
    }

    private UUID addOrder(UUID customer, UUID restaurant, UUID menu) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.\"Order\" (id, user_id, restaurant_id, state, subtotal) VALUES (?, ?, ?, 'PlacedState', 20)",
                id, customer, restaurant);
        jdbc.update("""
                INSERT INTO public."OrderLineItem" (id, order_id, menu_item_id, quantity, unit_price, menu_item_name)
                VALUES (?, ?, ?, 1, 20, 'Extra meal')
                """, UUID.randomUUID(), id, menu);
        jdbc.update("INSERT INTO public.\"Event\" (id, order_id, performed_by, action, new_state) VALUES (?, ?, ?, 'PLACE', 'PlacedState')",
                UUID.randomUUID(), id, customer);
        return id;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM public.\"" + table + "\"", Integer.class);
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        var rows = new LinkedHashMap<String, List<Map<String, Object>>>();
        rows.put("auth.users", jdbc.queryForList("SELECT * FROM auth.users ORDER BY id"));
        for (String table : List.of("User", "Customer", "Courier", "Restaurant", "MenuItem", "Order", "OrderLineItem", "Event", "Bill")) {
            String key = List.of("Customer", "Courier", "Restaurant").contains(table) ? "user_id" : "id";
            rows.put(table, jdbc.queryForList("SELECT * FROM public.\"" + table + "\" ORDER BY " + key));
        }
        return rows;
    }
    // Comparing full persisted rows catches stale children, extra bills and differences from the complete starting state.
}
