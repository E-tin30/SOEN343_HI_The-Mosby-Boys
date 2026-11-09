package com.mosbyboys.dishesbecrazy.technical.seed;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import com.mosbyboys.dishesbecrazy.domain.ord.state.IllegalTransitionException;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import static org.junit.jupiter.api.Assertions.*;

class DemoOrderFixtureTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final DemoFixtureLoader loader = new DemoFixtureLoader(json);

    @Test
    void acceptedOrdersDefineTheZeroThreeSixPreparationQueues() throws Exception {
        var fixtures = loader.load();
        var accepted = fixtures.orders().stream().filter(o -> o.state().equals("AcceptedState"))
                .collect(Collectors.groupingBy(DemoFixtures.OrderSeed::restaurantUsername, Collectors.counting()));

        assertEquals(Map.of("demo_restaurant_building", 3L, "demo_restaurant_busy", 6L), accepted);
        assertTrue(fixtures.orders().stream().noneMatch(o -> o.restaurantUsername().equals("demo_restaurant")));
        assertEquals(13, fixtures.orders().size());
        assertEquals(13, fixtures.orders().stream().mapToInt(o -> o.lines().size()).sum());
        assertEquals(35, fixtures.orders().stream().mapToInt(o -> o.events().size()).sum());
        for (String restaurant : accepted.keySet()) {
            var placementTimes = fixtures.orders().stream().filter(o -> o.state().equals("AcceptedState"))
                    .filter(o -> o.restaurantUsername().equals(restaurant))
                    .map(DemoFixtures.OrderSeed::placedAt).toList();
            assertEquals(placementTimes.stream().sorted().toList(), placementTimes);
            assertEquals(placementTimes.size(), placementTimes.stream().distinct().count());
        }
        for (var order : fixtures.orders()) {
            assertEquals("demo_customer", order.customerUsername());
            boolean building = order.restaurantUsername().equals("demo_restaurant_building");
            assertEquals(0, new BigDecimal(building ? "17.25" : "14.75").compareTo(order.subtotal()));
            assertEquals(building ? "Handmade Spinach Ravioli" : "Homemade Chicken Burrito", order.lines().getFirst().menuItemName());
            if (order.state().equals("AcceptedState")) {
                assertNull(order.courierUsername());
            }
        }
    }
    // Fixed timestamps preserve FIFO; validating fixtures does not populate the database or queues.

    @Test
    void terminalHistoryIncludesDeliveredRefundedRejectedAndCancelledOrders() throws Exception {
        var fixtures = loader.load();
        var states = fixtures.orders().stream().collect(Collectors.groupingBy(
                DemoFixtures.OrderSeed::state, Collectors.counting()));

        assertEquals(Map.of("AcceptedState", 9L, "DeliveredState", 1L, "RefundedState", 1L,
                "RejectedState", 1L, "CancelledState", 1L), states);
        var delivered = fixtures.orders().stream().filter(o -> o.state().equals("DeliveredState")).findFirst().orElseThrow();
        assertEquals("demo_courier", delivered.courierUsername());
        assertEquals(java.util.List.of("PLACE", "ACCEPT", "PREPARING", "READY", "PICKUP", "DELIVER"),
                delivered.events().stream().map(DemoFixtures.EventSeed::action).toList());
        assertEquals("OFF_DUTY", fixtures.accounts().stream().filter(a -> a.username().equals("demo_courier"))
                .findFirst().orElseThrow().state());
    }
    // A past delivery references its courier without changing the courier's current state.

    @Test
    void rejectedHistoryRequiresItsReason() throws Exception {
        var orders = orders();
        historical(orders, "RejectedState").remove("rejectionReason");

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @Test
    void deliveryHistoryRequiresTheAssignedCourier() throws Exception {
        var orders = orders();
        historical(orders, "DeliveredState").remove("courierUsername");

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @Test
    void courierCannotCancelTheCustomersPlacedOrder() throws Exception {
        var orders = orders();
        historical(orders, "CancelledState").get("events").get(1).asObject()
                .put("performedByUsername", "demo_courier");

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @Test
    void operatorRefundHistoryReplaysToRefundedState() throws Exception {
        var refunded = loader.load().orders().stream().filter(order -> order.state().equals("RefundedState"))
                .findFirst().orElseThrow();

        assertEquals("demo_courier", refunded.courierUsername());
        assertEquals(java.util.List.of("PLACE", "ACCEPT", "PREPARING", "READY", "PICKUP", "DELIVER", "REFUND"),
                refunded.events().stream().map(DemoFixtures.EventSeed::action).toList());
        var refund = refunded.events().getLast();
        assertEquals("demo_operator", refund.performedByUsername());
        assertEquals("DeliveredState", refund.previousState());
        assertEquals("RefundedState", refund.newState());
    }
    // The checked-in baseline itself must include a complete delivered purchase followed by its operator refund.

    @ParameterizedTest
    @ValueSource(strings = {"demo_customer", "demo_courier", "demo_restaurant_building"})
    void rejectsRefundsRecordedByNonOperators(String actor) throws Exception {
        var orders = orders();
        var events = historical(orders, "RefundedState").get("events").asArray();
        events.get(events.size() - 1).asObject().put("performedByUsername", actor);

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }
    // Owning or delivering a purchase does not authorize its customer, cook or courier to issue a refund.

    @Test
    void rejectsRefundHistoryThatSkipsDelivery() throws Exception {
        var orders = orders();
        var events = historical(orders, "RefundedState").get("events").asArray();
        events.remove(events.size() - 2);
        events.get(events.size() - 1).asObject().put("previousState", "PickedUpState");

        assertThrows(IllegalTransitionException.class, () -> load(orders));
    }
    // An operator REFUND event must follow delivery rather than skipping directly from pickup.

    @Test
    void ordersLinesAndEventsCannotBeChangedAfterLoading() throws Exception {
        var fixtures = loader.load();
        var order = fixtures.orders().getFirst();

        assertThrows(UnsupportedOperationException.class, () -> fixtures.orders().clear());
        assertThrows(UnsupportedOperationException.class, () -> order.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> order.events().clear());
    }
    // Callers share a stable snapshot rather than mutable baseline lists.

    @ParameterizedTest
    @CsvSource({"customerUsername,missing_customer", "customerUsername,demo_operator",
            "restaurantUsername,demo_customer", "courierUsername,demo_customer"})
    void rejectsMissingOrIncorrectAccountRoles(String field, String value) throws Exception {
        var orders = orders();
        orders.get(0).asObject().put(field, value);

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @Test
    void rejectsAnotherRestaurantsMenuItem() throws Exception {
        var orders = orders();
        orders.get(0).get("lines").get(0).asObject().put("menuItemId", "34300000-0000-4000-8000-000000000302");

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }
    // A real item still cannot be purchased under a different restaurant's order.

    @ParameterizedTest
    @ValueSource(strings = {"order", "line", "event"})
    void rejectsDuplicateIdsAcrossOrders(String kind) throws Exception {
        var orders = orders();
        var first = orders.get(0);
        var second = orders.get(1);
        if (kind.equals("line")) {
            first = first.get("lines").get(0);
            second = second.get("lines").get(0);
        } else if (kind.equals("event")) {
            first = first.get("events").get(0);
            second = second.get("events").get(0);
        }
        second.asObject().put("id", first.get("id").asString());

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @ParameterizedTest
    @ValueSource(strings = {"subtotal", "placedAt", "lines", "events"})
    void rejectsMissingOrderFields(String field) throws Exception {
        var orders = orders();
        orders.get(0).asObject().remove(field);

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @Test
    void rejectsAnIncorrectSubtotal() throws Exception {
        var orders = orders();
        orders.get(0).asObject().put("subtotal", new BigDecimal("17.00"));

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @ParameterizedTest
    @CsvSource({"quantity,0", "quantity,-1", "unitPrice,0", "unitPrice,-1"})
    void rejectsNonPositivePurchases(String field, int value) throws Exception {
        var orders = orders();
        orders.get(0).get("lines").get(0).asObject().put(field, value);

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @Test
    void historicalPricesAndRemainingStockAreIndependent() throws Exception {
        var orders = orders();
        orders.get(0).asObject().put("subtotal", new BigDecimal("15.00"));
        orders.get(0).get("lines").get(0).asObject().put("unitPrice", new BigDecimal("15.00"));

        var fixtures = load(orders);

        assertEquals(0, new BigDecimal("15.00").compareTo(fixtures.orders().getFirst().subtotal()));
        var menuItem = fixtures.menuItems().stream()
                .filter(item -> item.id().equals(fixtures.orders().getFirst().lines().getFirst().menuItemId()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("17.25").compareTo(menuItem.price()));
        assertEquals(10, menuItem.stockQuantity());
    }
    // Historical totals use purchased prices; loading does not consume the current remaining stock.

    @ParameterizedTest
    @CsvSource({"previousState,ReadyState", "newState,DeliveredState", "performedByUsername,demo_customer",
            "createdAt,2026-01-15T18:00:00Z", "action,UNKNOWN"})
    void rejectsBrokenHistory(String field, String value) throws Exception {
        var orders = orders();
        orders.get(0).get("events").get(1).asObject().put(field, value);

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @Test
    void rejectsTransitionsThatTheDomainDoesNotAllow() throws Exception {
        var orders = orders();
        var event = orders.get(0).get("events").get(1).asObject();
        event.put("action", "READY");
        event.put("newState", "ReadyState");

        assertThrows(IllegalTransitionException.class, () -> load(orders));
    }
    // A plausible state name is insufficient: PLACE cannot jump directly to READY.

    @Test
    void rejectsHistoryThatDoesNotReachTheSavedState() throws Exception {
        var orders = orders();
        orders.get(0).asObject().put("state", "ReadyState");

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @Test
    void requiresPlaceAsTheFirstEventAtThePlacementTime() throws Exception {
        var orders = orders();
        orders.get(0).get("events").get(0).asObject().put("createdAt", "2026-01-15T17:59:00Z");

        assertThrows(IllegalArgumentException.class, () -> load(orders));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "[null]"})
    void rejectsMissingOrderDatasets(String document) {
        assertThrows(IllegalArgumentException.class, () -> loader.load(new ClassPathResource("demo/profiles.json"),
                new ClassPathResource("demo/menu.json"),
                new ByteArrayResource(document.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    private ArrayNode orders() throws Exception {
        try (var input = new ClassPathResource("demo/orders.json").getInputStream()) {
            return json.readTree(input).asArray();
        }
    }

    private tools.jackson.databind.node.ObjectNode historical(ArrayNode orders, String state) {
        return orders.valueStream().filter(o -> o.get("state").asString().equals(state))
                .findFirst().orElseThrow().asObject();
    }

    private DemoFixtures load(ArrayNode orders) throws Exception {
        return loader.load(new ClassPathResource("demo/profiles.json"), new ClassPathResource("demo/menu.json"),
                new ByteArrayResource(json.writeValueAsBytes(orders)));
    }
    // Invalid cases mutate the production baseline while leaving unrelated fields valid.
}
