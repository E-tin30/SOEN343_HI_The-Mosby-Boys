package com.mosbyboys.dishesbecrazy.domain.ord;

import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.domain.reg.Role;

class RestaurantTest {

    private Restaurant restaurant(UUID id, RestaurantStatus status) {
        return restaurant(id, "Nonna's Kitchen", status);
    }

    private Restaurant restaurant(UUID id, String name, RestaurantStatus status) {
        return new Restaurant(id, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                name, status, new Location(45.5, -73.5));
    }

    // an open restaurant accepts orders
    @Test
    void openRestaurantAcceptsOrders() {
        assertTrue(restaurant(UUID.randomUUID(), RestaurantStatus.OPEN).acceptsOrders());
    }

    // pausing stops new orders, reopening allows them again
    @Test
    void pauseAndReopen() {
        Restaurant r = restaurant(UUID.randomUUID(), RestaurantStatus.OPEN);
        r.pause();
        assertEquals(RestaurantStatus.PAUSED, r.getStatus());
        assertFalse(r.acceptsOrders());
        r.reopen();
        assertTrue(r.acceptsOrders());
    }

    // a closed restaurant doesn't accept orders
    @Test
    void closedRestaurantDoesNotAcceptOrders() {
        Restaurant r = restaurant(UUID.randomUUID(), RestaurantStatus.OPEN);
        r.close();
        assertFalse(r.acceptsOrders());
    }

    // a restaurant is a user with the RESTAURANT role
    @Test
    void restaurantHasRestaurantRole() {
        assertEquals(Role.RESTAURANT, restaurant(UUID.randomUUID(), RestaurantStatus.OPEN).getRole());
    }

    // null id throws exception
    @Test
    void nullIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> restaurant(null, RestaurantStatus.OPEN));
    }

    // blank name throws exception
    @Test
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> restaurant(UUID.randomUUID(), " ", RestaurantStatus.OPEN));
    }

    // null status throws exception
    @Test
    void nullStatusThrows() {
        assertThrows(IllegalArgumentException.class, () -> restaurant(UUID.randomUUID(), "Resto", null));
    }
}
