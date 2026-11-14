package com.mosbyboys.dishesbecrazy.technical.seed;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueueLoader;
import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DemoOrderSeederTest {
    private JdbcTemplate jdbc;
    private DemoFixtures baseline;
    private DemoOrderSeeder seeder;
    private final Map<String, UUID> users = new HashMap<>();

    @BeforeEach
    void prepareDatabase() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        for (String statement : schema()) jdbc.execute(statement);
        var loader = new DemoFixtureLoader(JsonMapper.builder().build());
        baseline = loader.load();
        seeder = new DemoOrderSeeder(new DemoOrderStore(jdbc), new TransactionTemplate(new DataSourceTransactionManager(source)), loader);
        for (var account : baseline.accounts()) {
            UUID id = UUID.nameUUIDFromBytes(account.username().getBytes(StandardCharsets.UTF_8));
            users.put(account.username(), id);
            jdbc.update("INSERT INTO public.\"User\" (id, username, role) VALUES (?, ?, ?)", id, account.username(), account.role().name());
            if (account.role().name().equals("RESTAURANT"))
                jdbc.update("INSERT INTO public.\"Restaurant\" (user_id) VALUES (?)", id);
        }
        for (var item : baseline.menuItems()) {
            jdbc.update("INSERT INTO public.\"MenuItem\" (id, restaurant_id, stock_quantity) VALUES (?, ?, ?)",
                    item.id(), users.get(item.restaurantUsername()), item.stockQuantity());
        }
    }
    // Each test owns an isolated H2 database with the existing order/line/event columns and foreign-key relationships.

    @Test
    void storesTheCompleteOrderLineAndEventSnapshots() throws Exception {
        seeder.run();
        assertCounts(14, 14, 39);
        for (var order : baseline.orders()) {
            var row = jdbc.queryForMap("SELECT * FROM public.\"Order\" WHERE id = ?", order.id());
            assertEquals(users.get(order.customerUsername()), row.get("user_id"));
            assertEquals(users.get(order.restaurantUsername()), row.get("restaurant_id"));
            assertEquals(order.courierUsername() == null ? null : users.get(order.courierUsername()), row.get("courier_id"));
            assertEquals(order.state(), row.get("state"));
            assertEquals(order.rejectionReason(), row.get("rejection_reason"));
            assertEquals(0, order.subtotal().compareTo((BigDecimal) row.get("subtotal")));
            assertEquals(order.placedAt(), jdbc.queryForObject("SELECT created_at FROM public.\"Order\" WHERE id = ?",
                    (result, index) -> result.getTimestamp(1).toInstant(), order.id()));
            for (var line : order.lines()) {
                var saved = jdbc.queryForMap("SELECT * FROM public.\"OrderLineItem\" WHERE id = ?", line.id());
                assertEquals(order.id(), saved.get("order_id"));
                assertEquals(line.menuItemId(), saved.get("menu_item_id"));
                assertEquals(line.menuItemName(), saved.get("menu_item_name"));
                assertEquals(line.quantity(), saved.get("quantity"));
                assertEquals(0, line.unitPrice().compareTo((BigDecimal) saved.get("unit_price")));
            }
            for (var event : order.events()) {
                var saved = jdbc.queryForMap("SELECT * FROM public.\"Event\" WHERE id = ?", event.id());
                assertEquals(order.id(), saved.get("order_id"));
                assertEquals(users.get(event.performedByUsername()), saved.get("performed_by"));
                assertEquals(event.action(), saved.get("action"));
                assertEquals(event.previousState(), saved.get("previous_state"));
                assertEquals(event.newState(), saved.get("new_state"));
                assertEquals(event.createdAt(), jdbc.queryForObject("SELECT created_at FROM public.\"Event\" WHERE id = ?",
                        (result, index) -> result.getTimestamp(1).toInstant(), event.id()));
            }
        }
        assertEquals(10, jdbc.queryForObject("SELECT stock_quantity FROM public.\"MenuItem\" WHERE id = ?", Integer.class,
                baseline.orders().getFirst().lines().getFirst().menuItemId()));
    }

    @Test
    void repeatRunsLeaveAdvancedOrdersAndTheirAdditionalEventsAlone() throws Exception {
        seeder.run();
        var order = baseline.orders().getFirst();
        jdbc.update("UPDATE public.\"Order\" SET state = 'PreparingState' WHERE id = ?", order.id());
        jdbc.update("""
                INSERT INTO public."Event" (id, order_id, performed_by, action, previous_state, new_state, created_at)
                VALUES (?, ?, ?, 'PREPARING', 'AcceptedState', 'PreparingState', ?)
                """, UUID.randomUUID(), order.id(), users.get(order.restaurantUsername()), Timestamp.from(Instant.now()));

        seeder.run();

        assertCounts(14, 14, 40);
        assertEquals("PreparingState", jdbc.queryForObject("SELECT state FROM public.\"Order\" WHERE id = ?", String.class, order.id()));
    }
    // Initial seeding never rewinds a demo already in progress or adds the old history again.

    @Test
    void missingProfilesPreventEveryOrderInsert() {
        jdbc.update("DELETE FROM public.\"User\" WHERE username = 'demo_customer'");
        assertThrows(IllegalStateException.class, seeder::run);
        assertCounts(0, 0, 0);
    }

    @Test
    void aMissingLaterMenuItemRollsBackEarlierOrderInserts() {
        UUID busyItem = baseline.orders().stream().filter(order -> order.restaurantUsername().equals("demo_restaurant_busy"))
                .findFirst().orElseThrow().lines().getFirst().menuItemId();
        jdbc.update("DELETE FROM public.\"MenuItem\" WHERE id = ?", busyItem);
        assertThrows(IllegalStateException.class, seeder::run);
        assertCounts(0, 0, 0);
    }
    // Building's three orders were processed first; the real transaction removes them after the later failure.

    @Test
    void conflictingEventIdsRollBackNewRowsAndPreserveExistingData() {
        UUID existing = UUID.randomUUID();
        existingOrder(existing, users.get("demo_customer"), users.get("demo_restaurant"));
        var last = baseline.orders().getLast();
        UUID reserved = last.events().getLast().id();
        jdbc.update("""
                INSERT INTO public."Event" (id, order_id, performed_by, action, new_state, created_at)
                VALUES (?, ?, ?, 'PLACE', 'PlacedState', ?)
                """, reserved, existing, users.get("demo_customer"), Timestamp.from(Instant.now()));

        assertThrows(DataAccessException.class, seeder::run);

        assertCounts(1, 0, 1);
        assertEquals(existing, jdbc.queryForObject("SELECT order_id FROM public.\"Event\" WHERE id = ?", UUID.class, reserved));
    }
    // A colliding ID fails visibly instead of skipping a child row and leaving partial fixture history.

    @Test
    void existingOrderIdsCannotBeClaimedForDifferentOwners() {
        existingOrder(baseline.orders().getFirst().id(), users.get("demo_customer"), users.get("demo_restaurant"));
        assertThrows(IllegalStateException.class, seeder::run);
        assertCounts(1, 0, 0);
    }

    @Test
    void storedMenusMustBelongToTheResolvedRestaurant() {
        var order = baseline.orders().getFirst();
        jdbc.update("UPDATE public.\"MenuItem\" SET restaurant_id = ? WHERE id = ?",
                users.get("demo_restaurant"), order.lines().getFirst().menuItemId());
        assertThrows(IllegalStateException.class, seeder::run);
        assertCounts(0, 0, 0);
    }

    @Test
    void startupSeedsBeforeLoadingTheZeroThreeSixPreparationQueues() throws Exception {
        var service = mock(OrderService.class);
        when(service.findInStates(anyList())).thenAnswer(call -> jdbc.query("""
                SELECT * FROM public."Order" WHERE state IN ('PlacedState', 'AcceptedState', 'ReadyState') ORDER BY created_at ASC
                """, (row, index) -> Order.reconstitute(row.getObject("id", UUID.class), OrderStates.fromName(row.getString("state")),
                row.getObject("user_id", UUID.class), row.getObject("restaurant_id", UUID.class), null,
                List.of(), row.getTimestamp("created_at").toInstant(), null, row.getBigDecimal("subtotal"))));
        var queues = new OrderQueues();
        var runners = new ArrayList<CommandLineRunner>(List.of(new OrderQueueLoader(service, queues), seeder));
        AnnotationAwareOrderComparator.sort(runners);
        for (var runner : runners) runner.run();

        assertEquals(0, queues.preparation(users.get("demo_restaurant")).size());
        assertEquals(3, queues.preparation(users.get("demo_restaurant_building")).size());
        assertEquals(6, queues.preparation(users.get("demo_restaurant_busy")).size());
        assertEquals(baseline.orders().getFirst().id(), queues.preparation(users.get("demo_restaurant_building")).peek());
        assertEquals(0, queues.ready(users.get("demo_restaurant_building")).size());
    }
    // Spring's actual runner ordering and queue routing read the seeded database; the service boundary is a JDBC test adapter.

    private void existingOrder(UUID id, UUID customer, UUID restaurant) {
        jdbc.update("""
                INSERT INTO public."Order" (id, user_id, restaurant_id, state, created_at, subtotal)
                VALUES (?, ?, ?, 'PlacedState', ?, 16.00)
                """, id, customer, restaurant, Timestamp.from(Instant.now()));
    }

    private void assertCounts(int orders, int lines, int events) {
        assertEquals(orders, jdbc.queryForObject("SELECT COUNT(*) FROM public.\"Order\"", Integer.class));
        assertEquals(lines, jdbc.queryForObject("SELECT COUNT(*) FROM public.\"OrderLineItem\"", Integer.class));
        assertEquals(events, jdbc.queryForObject("SELECT COUNT(*) FROM public.\"Event\"", Integer.class));
    }

    private List<String> schema() {
        return List.of("""
                CREATE TABLE public."User" (id uuid PRIMARY KEY, username varchar NOT NULL UNIQUE, role varchar NOT NULL)
                """, """
                CREATE TABLE public."Restaurant" (user_id uuid PRIMARY KEY REFERENCES public."User"(id))
                """, """
                CREATE TABLE public."MenuItem" (id uuid PRIMARY KEY, restaurant_id uuid NOT NULL REFERENCES public."Restaurant"(user_id), stock_quantity integer)
                """, """
                CREATE TABLE public."Order" (id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES public."User"(id),
                    restaurant_id uuid NOT NULL REFERENCES public."Restaurant"(user_id), courier_id uuid REFERENCES public."User"(id),
                    state varchar NOT NULL, created_at timestamp with time zone NOT NULL, subtotal numeric(12,2), rejection_reason varchar)
                """, """
                CREATE TABLE public."OrderLineItem" (id uuid PRIMARY KEY, order_id uuid NOT NULL REFERENCES public."Order"(id),
                    menu_item_id uuid NOT NULL REFERENCES public."MenuItem"(id), quantity integer NOT NULL CHECK(quantity > 0),
                    unit_price numeric(12,2) NOT NULL, menu_item_name varchar NOT NULL, created_at timestamp with time zone NOT NULL)
                """, """
                CREATE TABLE public."Event" (id uuid PRIMARY KEY, order_id uuid NOT NULL REFERENCES public."Order"(id),
                    performed_by uuid REFERENCES public."User"(id), action varchar, previous_state varchar,
                    new_state varchar NOT NULL, created_at timestamp with time zone NOT NULL)
                """);
    }
}
