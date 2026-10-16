package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.reg.Courier;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.Operator;
import com.mosbyboys.dishesbecrazy.domain.reg.RestaurantOwner;

public class PermissionPolicyTest {

    private final PermissionPolicy policy = new PermissionPolicy();

    // Helper functions to create users and orders
    private Customer customer(UUID customerId) {
        return new Customer(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                customerId, OffsetDateTime.now(), "123 Street");
    }

    private RestaurantOwner restaurant(UUID restaurantId) {
        return new RestaurantOwner(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                restaurantId, OffsetDateTime.now(), "Resto", "OPEN");
    }

    private Operator operator() {
        return new Operator(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash");
    }

    private Courier courier() {
        return new Courier(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                UUID.randomUUID(), OffsetDateTime.now(), "OFF_DUTY", "Montreal");
    }

    private Order orderFor(UUID customerId, UUID restaurantId) {
        OrderLine line = new OrderLine(UUID.randomUUID(), 1, new BigDecimal("10.00"));
        return new Order(customerId, restaurantId, List.of(line));
    }

    // check that any customer can place an order
    @Test
    void customerCanPlace() {
        assertDoesNotThrow(() -> policy.checkCanPlace(customer(UUID.randomUUID())));
    }

    // check that restaurants cannot place an order
    @Test
    void restaurantCannotPlace() {
        assertThrows(PermissionDeniedException.class, () -> policy.checkCanPlace(restaurant(UUID.randomUUID())));
    }

    // check that operator cannot place an order
    @Test
    void operatorCannotPlace() {
        assertThrows(PermissionDeniedException.class, () -> policy.checkCanPlace(operator()));
    }

    // check that customer can cancel their order
    @Test
    void customerCanCancelOwnPlacedOrder() {
        UUID customerId = UUID.randomUUID();
        Order order = orderFor(customerId, UUID.randomUUID());

        assertDoesNotThrow(() -> policy.checkCanCancel(customer(customerId), order));
    }

    // check that customer cannot cancel someone else's order
    @Test
    void customerCannotCancelOtherCustomersOrder() {
        Order order = orderFor(UUID.randomUUID(), UUID.randomUUID());

        assertThrows(PermissionDeniedException.class, () -> policy.checkCanCancel(customer(UUID.randomUUID()), order));
    }

    // check that customer cannot cancel an order that has been accepted by restaurant
    @Test
    void customerCannotCancelAcceptedOrder() {
        UUID customerId = UUID.randomUUID();
        Order order = orderFor(customerId, UUID.randomUUID());
        order.accept();

        assertThrows(PermissionDeniedException.class, () -> policy.checkCanCancel(customer(customerId), order));
    }

    // check that operator can cancel an order after it has been accepted
    @Test
    void operatorCanCancelAcceptedOrder() {
        Order order = orderFor(UUID.randomUUID(), UUID.randomUUID());
        order.accept();

        assertDoesNotThrow(() -> policy.checkCanCancel(operator(), order));
    }

    // check that a restaurant has access to an order it owns
    @Test
    void restaurantCanActOnOwnOrder() {
        UUID restaurantId = UUID.randomUUID();
        Order order = orderFor(UUID.randomUUID(), restaurantId);

        assertDoesNotThrow(() -> policy.checkCanActAsRestaurant(restaurant(restaurantId), order));
    }

    // check that a restaurant can't access an order placed to another restaurant
    @Test
    void restaurantCannotActOnOtherRestaurantsOrder() {
        Order order = orderFor(UUID.randomUUID(), UUID.randomUUID());

        assertThrows(PermissionDeniedException.class, () -> policy.checkCanActAsRestaurant(restaurant(UUID.randomUUID()), order));
    }

    // check that operator doesn't have access to edit a restaurants orders
    @Test
    void operatorCannotActAsRestaurant() {
        Order order = orderFor(UUID.randomUUID(), UUID.randomUUID());

        assertThrows(PermissionDeniedException.class,
                () -> policy.checkCanActAsRestaurant(operator(), order));
    }

    // check that customer can't see other customer's orders
    @Test
    void customerCannotViewOtherCustomersOrder() {
        Order order = orderFor(UUID.randomUUID(), UUID.randomUUID());

        assertThrows(PermissionDeniedException.class, () -> policy.checkCanView(customer(UUID.randomUUID()), order));
    }

    // check that a customer can see their own order
    @Test
    void customerCanViewOwnOrder() {
        UUID customerId = UUID.randomUUID();
        Order order = orderFor(customerId, UUID.randomUUID());

        assertDoesNotThrow(() -> policy.checkCanView(customer(customerId), order));
    }

    // check that if the caller is null then exception is thrown
    @Test
    void nullCallerIsDenied() {
        Order order = orderFor(UUID.randomUUID(), UUID.randomUUID());

        assertThrows(PermissionDeniedException.class, () -> policy.checkCanView(null, order));
    }

    // check that courier cannot view order details (this is just for now since DLV not implemented)
    @Test
    void courierCannotViewOrder() {
        Order order = orderFor(UUID.randomUUID(), UUID.randomUUID());

        assertThrows(PermissionDeniedException.class, () -> policy.checkCanView(courier(), order));
    }

    // check that operator can view all orders
    @Test
    void operatorCanViewAnyOrder() {
        Order order = orderFor(UUID.randomUUID(), UUID.randomUUID());

        assertDoesNotThrow(() -> policy.checkCanView(operator(), order));
    }
}