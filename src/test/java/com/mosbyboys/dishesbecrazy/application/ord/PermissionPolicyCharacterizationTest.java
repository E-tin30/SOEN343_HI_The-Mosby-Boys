package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.domain.reg.Courier;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.Operator;
import com.mosbyboys.dishesbecrazy.domain.reg.User;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionPolicyCharacterizationTest {

    private final PermissionPolicy policy = new PermissionPolicy();

    @Test
    void placingWithoutACallerPreservesTheError() {
        assertDenied("No caller.", () -> policy.checkCanPlace(null));
    }

    @ParameterizedTest(name = "{0}: missing caller")
    @MethodSource("orderChecks")
    void orderCheckRejectsMissingCaller(String command, BiConsumer<User, Order> check) {
        assertDenied("No caller.", () -> check.accept(null, order()));
    }

    @ParameterizedTest(name = "{0}: missing order")
    @MethodSource("orderChecks")
    void orderCheckRejectsMissingOrderBeforeCheckingRole(String command, BiConsumer<User, Order> check) {
        assertDenied("No order.", () -> check.accept(customer(), null));
    }

    @ParameterizedTest(name = "{0}: both inputs missing")
    @MethodSource("orderChecks")
    void orderCheckReportsMissingCallerFirst(String command, BiConsumer<User, Order> check) {
        assertDenied("No caller.", () -> check.accept(null, null));
    }

    @ParameterizedTest(name = "{0}: missing caller")
    @MethodSource("restaurantChecks")
    void restaurantCheckRejectsMissingCaller(String command, BiConsumer<User, Restaurant> check) {
        Restaurant target = restaurant(UUID.randomUUID(), RestaurantStatus.OPEN);
        assertDenied("No caller.", () -> check.accept(null, target));
    }

    @ParameterizedTest(name = "{0}: missing restaurant")
    @MethodSource("restaurantChecks")
    void restaurantCheckRejectsMissingRestaurantBeforeCheckingRole(
            String command, BiConsumer<User, Restaurant> check) {
        assertDenied("No restaurant.", () -> check.accept(customer(), null));
    }

    @ParameterizedTest(name = "{0}: both inputs missing")
    @MethodSource("restaurantChecks")
    void restaurantCheckReportsMissingCallerFirst(String command, BiConsumer<User, Restaurant> check) {
        assertDenied("No caller.", () -> check.accept(null, null));
    }
    // These cases preserve validation order as well as the exception type and message.

    @ParameterizedTest(name = "operator can {0} any restaurant")
    @MethodSource("restaurantManagementChecks")
    void operatorCanManageRestaurantInEveryStatus(String action, BiConsumer<User, Restaurant> check) {
        for (RestaurantStatus status : RestaurantStatus.values()) {
            Restaurant target = restaurant(UUID.randomUUID(), status);
            assertDoesNotThrow(() -> check.accept(operator(), target), status.name());
            assertEquals(status, target.getStatus());
        }
    }

    @ParameterizedTest(name = "owner can {0} their restaurant by id")
    @MethodSource("restaurantManagementChecks")
    void matchingRestaurantIdIsAllowedEvenForDifferentObjects(
            String action, BiConsumer<User, Restaurant> check) {
        UUID restaurantId = UUID.randomUUID();
        Restaurant caller = restaurant(restaurantId, RestaurantStatus.OPEN);
        for (RestaurantStatus status : RestaurantStatus.values()) {
            Restaurant target = restaurant(restaurantId, status);
            assertDoesNotThrow(() -> check.accept(caller, target), status.name());
            assertEquals(status, target.getStatus());
        }
    }
    // Permission depends on role and identity; these guards do not change restaurant status.

    @ParameterizedTest(name = "unauthorized caller cannot {0} a restaurant")
    @MethodSource("restaurantManagementChecks")
    void unauthorizedRolesAndOtherOwnersKeepTheActionSpecificError(
            String action, BiConsumer<User, Restaurant> check) {
        List<User> deniedCallers = List.of(customer(), courier(),
                restaurant(UUID.randomUUID(), RestaurantStatus.OPEN));
        for (RestaurantStatus status : RestaurantStatus.values()) {
            Restaurant target = restaurant(UUID.randomUUID(), status);
            for (User caller : deniedCallers) {
                assertDenied("Only an operator or the restaurant itself can " + action + " a restaurant.",
                        () -> check.accept(caller, target));
                assertEquals(status, target.getStatus());
            }
        }
    }
    // Pause, open and close share a decision, but expose different denial messages.

    private Stream<Arguments> orderChecks() {
        return Stream.of(
                Arguments.of("cancel", (BiConsumer<User, Order>) policy::checkCanCancel),
                Arguments.of("act as restaurant", (BiConsumer<User, Order>) policy::checkCanActAsRestaurant),
                Arguments.of("respond to offer", (BiConsumer<User, Order>) policy::checkCanRespondToOffer),
                Arguments.of("act as courier", (BiConsumer<User, Order>) policy::checkCanActAsCourier),
                Arguments.of("refund", (BiConsumer<User, Order>) policy::checkCanRefund),
                Arguments.of("view", (BiConsumer<User, Order>) policy::checkCanView),
                Arguments.of("reassign", (BiConsumer<User, Order>) policy::checkCanReassign));
    }

    private Stream<Arguments> restaurantChecks() {
        return Stream.concat(restaurantManagementChecks(), Stream.of(
                Arguments.of("browse menu", (BiConsumer<User, Restaurant>) policy::checkCanBrowseMenu)));
    }

    private Stream<Arguments> restaurantManagementChecks() {
        return Stream.of(
                Arguments.of("pause", (BiConsumer<User, Restaurant>) policy::checkCanPause),
                Arguments.of("open", (BiConsumer<User, Restaurant>) policy::checkCanOpen),
                Arguments.of("close", (BiConsumer<User, Restaurant>) policy::checkCanClose));
    }
    // Method sources run the same observable contract against each existing public entry point.

    private void assertDenied(String message, Executable check) {
        PermissionDeniedException exception = assertThrows(PermissionDeniedException.class, check);
        assertEquals(message, exception.getMessage());
    }

    private Order order() {
        OrderLine line = new OrderLine(UUID.randomUUID(), 1, new BigDecimal("10.00"), null);
        return new Order(UUID.randomUUID(), UUID.randomUUID(), List.of(line), new BigDecimal("10.00"));
    }

    private Restaurant restaurant(UUID id, RestaurantStatus status) {
        return new Restaurant(id, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                "Resto", status, null);
    }

    private Customer customer() {
        return new Customer(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                "123 Street", new Location(45.5, -73.6));
    }

    private Operator operator() {
        return new Operator(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash");
    }

    private Courier courier() {
        return new Courier(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                CourierState.OFFLINE, new Location(45.5, -73.6));
    }
}
