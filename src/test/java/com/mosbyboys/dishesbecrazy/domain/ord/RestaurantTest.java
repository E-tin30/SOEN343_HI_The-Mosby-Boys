package com.mosbyboys.dishesbecrazy.domain.ord;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestaurantTest {

    private Restaurant restaurant(UUID ownerId, RestaurantStatus status) {
        return new Restaurant(UUID.randomUUID(), ownerId, "Nonna's Kitchen", status,
                new Location(45.5, -73.5), Instant.now());
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

    // only the owner's id matches
    @Test
    void isOwnedByOnlyMatchesOwner() {
        UUID ownerId = UUID.randomUUID();
        Restaurant r = restaurant(ownerId, RestaurantStatus.OPEN);
        assertTrue(r.isOwnedBy(ownerId));
        assertFalse(r.isOwnedBy(UUID.randomUUID()));
        assertFalse(r.isOwnedBy(null));
    }

    // blank name throws exception
    @Test
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Restaurant(UUID.randomUUID(), UUID.randomUUID(),
                " ", RestaurantStatus.OPEN, null, Instant.now()));
    }

    // null status throws exception
    @Test
    void nullStatusThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Restaurant(UUID.randomUUID(), UUID.randomUUID(),
                "Resto", null, null, Instant.now()));
    }
}
