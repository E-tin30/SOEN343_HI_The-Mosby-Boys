package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.domain.reg.Courier;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.Operator;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;

public class PermissionPolicyTest {

    private final PermissionPolicy policy = new PermissionPolicy();

    // Helper functions to create users and orders
    private Customer customer(UUID customerId) {
        return new Customer(customerId, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                "123 Street", new Location(45.5, -73.6));
    }
    private Restaurant restaurant(UUID restaurantId) {
        return restaurantWithStatus(restaurantId, RestaurantStatus.OPEN);
    }

    private Operator operator() {
        return new Operator(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash");
    }

    private Courier courier() {
        return new Courier(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                CourierState.OFFLINE, new Location(45.5, -73.6));
    }

    private Order orderFor(UUID customerId, UUID restaurantId) {
        OrderLine line = new OrderLine(UUID.randomUUID(), 1, new BigDecimal("10.00"), null);
        return new Order(customerId, restaurantId, List.of(line), new BigDecimal("10.00"));
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

    // --- Browse menu (UC-ORD-01) ---

    private Restaurant restaurantWithStatus(UUID restaurantId, RestaurantStatus status) {
        return new Restaurant(restaurantId, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                "Resto", status, null);
    }

    // check that customers can browse an open restaurant's menu
    @Test
    void customerCanBrowseOpenRestaurant() {
        Restaurant open = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);

        assertDoesNotThrow(() -> policy.checkCanBrowseMenu(customer(UUID.randomUUID()), open));
    }

    // check that customers cannot browse a paused restaurant's menu
    @Test
    void customerCannotBrowsePausedRestaurant() {
        Restaurant paused = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.PAUSED);

        assertThrows(PermissionDeniedException.class, () -> policy.checkCanBrowseMenu(customer(UUID.randomUUID()), paused));
    }

    // check that owners can browse their own menu, even while paused
    @Test
    void ownerCanBrowseOwnPausedRestaurant() {
        Restaurant paused = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.PAUSED);

        assertDoesNotThrow(() -> policy.checkCanBrowseMenu(paused, paused));
    }

    // check that owners cannot browse another restaurant's menu
    @Test
    void ownerCannotBrowseOtherRestaurant() {
        Restaurant other = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);

        assertThrows(PermissionDeniedException.class, () -> policy.checkCanBrowseMenu(restaurant(UUID.randomUUID()), other));
    }

    // check that operators can browse any menu, even a closed restaurant's
    @Test
    void operatorCanBrowseClosedRestaurant() {
        Restaurant closed = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.CLOSED);

        assertDoesNotThrow(() -> policy.checkCanBrowseMenu(operator(), closed));
    }

    // check that couriers cannot browse menus
    @Test
    void courierCannotBrowseMenu() {
        Restaurant open = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);

        assertThrows(PermissionDeniedException.class, () -> policy.checkCanBrowseMenu(courier(), open));
    }

    // check that a null caller is denied
    @Test
    void nullCallerCannotBrowseMenu() {
        Restaurant open = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);

        assertThrows(PermissionDeniedException.class, () -> policy.checkCanBrowseMenu(null, open));
    }

    // check that an operator can pause any restaurant
    @Test
    void operatorCanPauseAnyRestaurant() {
        Operator op = operator();
        Restaurant restaurant = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);
        assertDoesNotThrow(() -> policy.checkCanPause(op, restaurant));
    }

    // check that a restaurant can pause itself (caller and target have the same id)
    @Test
    void ownerCanPauseOwnRestaurant() {
        Restaurant restaurant = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);
        assertDoesNotThrow(() -> policy.checkCanPause(restaurant, restaurant));
    }

    // check that a restaurant cannot pause another restaurant (two different ids)
    @Test
    void ownerCannotPauseOtherRestaurant() {
        Restaurant restaurant1 = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);
        Restaurant restaurant2 = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);
        assertThrows(PermissionDeniedException.class, () -> policy.checkCanPause(restaurant1, restaurant2));
    }

    // check that customers cannot pause a restaurant
    @Test
    void customerCannotPauseRestaurant() {
        Restaurant restaurant = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);
        assertThrows(PermissionDeniedException.class, () -> policy.checkCanPause(customer(UUID.randomUUID()), restaurant));
    }

    // check that couriers cannot pause a restaurant
    @Test
    void courierCannotPauseRestaurant() {
        Restaurant restaurant = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);
        assertThrows(PermissionDeniedException.class, () -> policy.checkCanPause(courier(), restaurant));
    }

    // check that a null caller cannot pause a restaurant
    @Test
    void nullCallerCannotPause() {
        Restaurant restaurant = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.OPEN);
        assertThrows(PermissionDeniedException.class, () -> policy.checkCanPause(null, restaurant));

    }

    // check that an operator can open any restaurant
    @Test
    void operatorCanOpenAnyRestaurant() {
        Operator op = operator();
        Restaurant restaurant = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.PAUSED);
        assertDoesNotThrow(() -> policy.checkCanOpen(op, restaurant));
    }

    // check that a restaurant cannot open itself, only an operator can (Appendix A, R-DASH-01)
    @Test
    void ownerCannotOpenOwnRestaurant() {
        Restaurant restaurant = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.PAUSED);
        assertThrows(PermissionDeniedException.class, () -> policy.checkCanOpen(restaurant, restaurant));
    }

    // check that customers cannot open a restaurant
    @Test
    void customerCannotOpenRestaurant() {
        Restaurant restaurant = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.PAUSED);
        assertThrows(PermissionDeniedException.class, () -> policy.checkCanOpen(customer(UUID.randomUUID()), restaurant));
    }

    // check that couriers cannot open a restaurant
    @Test
    void courierCannotOpenRestaurant() {
        Restaurant restaurant = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.PAUSED);
        assertThrows(PermissionDeniedException.class, () -> policy.checkCanOpen(courier(), restaurant));
    }

    // check that a null caller cannot open a restaurant
    @Test
    void nullCallerCannotOpen() {
        Restaurant restaurant = restaurantWithStatus(UUID.randomUUID(), RestaurantStatus.PAUSED);
        assertThrows(PermissionDeniedException.class, () -> policy.checkCanOpen(null, restaurant));
    }
}
