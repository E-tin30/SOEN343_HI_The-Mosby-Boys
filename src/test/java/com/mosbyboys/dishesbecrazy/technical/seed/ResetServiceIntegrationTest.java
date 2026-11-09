package com.mosbyboys.dishesbecrazy.technical.seed;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mosbyboys.dishesbecrazy.application.dash.ResetService;
import com.mosbyboys.dishesbecrazy.application.ord.OrderQueueLoader;
import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;
import com.mosbyboys.dishesbecrazy.technical.security.DemoStateAccess;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.domain.reg.User;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ResetServiceIntegrationTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private DemoFixtures baseline;
    private Map<String, UUID> users;
    private OrderQueues queues;
    private ResetService reset;
    private final AtomicBoolean failQueueRead = new AtomicBoolean();

    @BeforeEach
    void prepareDatabaseAndQueues() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("demo/restoration-schema.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        var fixtures = new DemoFixtureLoader(JsonMapper.builder().build());
        baseline = fixtures.load();
        users = baseline.accounts().stream().collect(Collectors.toMap(DemoFixtures.Account::username, account -> UUID.randomUUID()));
        for (var account : baseline.accounts()) {
            jdbc.update("INSERT INTO auth.users (id, email) VALUES (?, ?)", users.get(account.username()), account.email());
        }
        var database = new DemoDataRestorer(jdbc, transaction, fixtures, new DemoCatalogueRestorer(jdbc), new DemoOrderStore(jdbc));
        var registration = mock(REGFacade.class);
        var operator = mock(User.class);
        when(operator.getRole()).thenReturn(Role.OPERATOR);
        when(registration.getCurrentUser(users.get("demo_operator"))).thenReturn(operator);
        var orders = mock(OrderService.class);
        when(orders.findInStates(anyList())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Restoration must commit before queue loading");
            if (failQueueRead.get()) throw new DataAccessResourceFailureException("Queue snapshot unavailable");
            return new NamedParameterJdbcTemplate(jdbc).query("""
                    SELECT * FROM public."Order" WHERE state IN (:states) ORDER BY created_at ASC
                    """, Map.of("states", call.getArgument(0)), (row, index) -> Order.reconstitute(
                    row.getObject("id", UUID.class), OrderStates.fromName(row.getString("state")),
                    row.getObject("user_id", UUID.class), row.getObject("restaurant_id", UUID.class),
                    row.getObject("courier_id", UUID.class), List.of(), row.getTimestamp("created_at").toInstant(),
                    row.getString("rejection_reason"), row.getBigDecimal("subtotal")));
        });
        queues = new OrderQueues();
        reset = new ResetService(registration, database, new OrderQueueLoader(orders, queues), new DemoStateAccess());
    }
    // Restoration and queue rebuilding are real; only caller lookup and the OrderService read boundary use test adapters.

    @Test
    void walkthroughThenRestorationReturnsBothDatabaseAndQueuesToTheBaseline() throws Exception {
        restore();
        var retainedPreparation = queues.preparation(users.get("demo_restaurant_building"));
        var progressed = baseline.orders().getFirst();
        jdbc.update("UPDATE public.\"Order\" SET state = 'ReadyState' WHERE id = ?", progressed.id());
        retainedPreparation.remove(progressed.id());
        var retainedReady = queues.ready(users.get(progressed.restaurantUsername()));
        retainedReady.enqueue(progressed.id());
        jdbc.update("UPDATE public.\"Restaurant\" SET status = 'PAUSED' WHERE user_id = ?", users.get("demo_restaurant_building"));
        jdbc.update("UPDATE public.\"MenuItem\" SET stock_quantity = 0, status = 'SOLD_OUT' WHERE id = ?", progressed.lines().getFirst().menuItemId());
        UUID extraOrder = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO public."Order" (id, user_id, restaurant_id, state, subtotal) VALUES (?, ?, ?, 'PlacedState', 16)
                """, extraOrder, users.get("demo_customer"), users.get("demo_restaurant"));
        var retainedPlaced = queues.placed(users.get("demo_restaurant"));
        retainedPlaced.enqueue(extraOrder);

        restore();

        assertBaselineQueues();
        assertEquals("AcceptedState", state(progressed.id()));
        assertEquals("OPEN", jdbc.queryForObject("SELECT status FROM public.\"Restaurant\" WHERE user_id = ?",
                String.class, users.get("demo_restaurant_building")));
        assertEquals(10, jdbc.queryForObject("SELECT stock_quantity FROM public.\"MenuItem\" WHERE id = ?",
                Integer.class, progressed.lines().getFirst().menuItemId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM public.\"Order\" WHERE id = ?", Integer.class, extraOrder));
        assertEquals(28, jdbc.queryForObject("SELECT COUNT(*) FROM public.\"Event\"", Integer.class));
        assertEquals(0, retainedPlaced.size());
        assertEquals(0, retainedReady.size());
        assertEquals(0, retainedPreparation.size());

        restore();
        assertBaselineQueues();
    }
    // Held references are cleared too; later callers obtain the rebuilt registry queues rather than stale waiting lists.

    @Test
    void aQueueReadFailureAfterDatabaseCommitIsReportedAndRetryRepairsMemory() throws Exception {
        restore();
        var progressed = baseline.orders().getFirst();
        jdbc.update("UPDATE public.\"Order\" SET state = 'PreparingState' WHERE id = ?", progressed.id());
        var preparation = queues.preparation(users.get(progressed.restaurantUsername()));
        preparation.remove(progressed.id());
        failQueueRead.set(true);

        assertThrows(DataAccessResourceFailureException.class, this::restore);

        assertEquals("AcceptedState", state(progressed.id()));
        assertEquals(2, preparation.size());
        assertSame(preparation, queues.preparation(users.get(progressed.restaurantUsername())));
        failQueueRead.set(false);

        restore();

        assertBaselineQueues();
        assertEquals(0, preparation.size());
    }
    // The database has committed when the read fails. Retain the old queues, report failure, and repeat both steps on retry.

    @Test
    void anOuterTransactionCannotDelayDatabaseCommitUntilAfterQueueReplacement() throws Exception {
        restore();
        jdbc.update("UPDATE public.\"Restaurant\" SET status = 'PAUSED' WHERE user_id = ?", users.get("demo_restaurant_building"));

        transaction.executeWithoutResult(status -> {
            var error = assertThrows(IllegalStateException.class, this::restore);
            assertEquals("Demo restoration must start outside an existing transaction.", error.getMessage());
        });

        assertEquals("PAUSED", jdbc.queryForObject("SELECT status FROM public.\"Restaurant\" WHERE user_id = ?",
                String.class, users.get("demo_restaurant_building")));
        assertBaselineQueues();
    }

    private void restore() throws Exception {
        reset.restoreDatabaseAndOrderQueues(users.get("demo_operator"));
    }

    private String state(UUID id) {
        return jdbc.queryForObject("SELECT state FROM public.\"Order\" WHERE id = ?", String.class, id);
    }

    private void assertBaselineQueues() {
        for (var account : baseline.accounts()) {
            if (account.role() != Role.RESTAURANT) continue;
            UUID id = users.get(account.username());
            var expected = baseline.orders().stream().filter(order -> order.restaurantUsername().equals(account.username())
                    && order.state().equals("AcceptedState")).toList();
            assertEquals(expected.size(), queues.preparation(id).size());
            for (var order : expected) assertTrue(queues.preparation(id).contains(order.id()));
            if (!expected.isEmpty()) assertEquals(expected.getFirst().id(), queues.preparation(id).peek());
            assertEquals(0, queues.placed(id).size());
            assertEquals(0, queues.ready(id).size());
        }
    }
}
