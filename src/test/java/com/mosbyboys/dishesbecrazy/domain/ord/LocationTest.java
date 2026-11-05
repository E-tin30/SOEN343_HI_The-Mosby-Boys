package com.mosbyboys.dishesbecrazy.domain.ord;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LocationTest {

    @Test
    void validCoordinatesAreKept() {
        Location location = new Location(45.5017, -73.5673);

        assertEquals(45.5017, location.latitude());
        assertEquals(-73.5673, location.longitude());
    }

    @Test
    void edgesOfTheRangeAreAllowed() {
        assertDoesNotThrow(() -> new Location(90, 180));
        assertDoesNotThrow(() -> new Location(-90, -180));
    }

    @Test
    void latitudeOutOfRangeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Location(91, 0));
        assertThrows(IllegalArgumentException.class, () -> new Location(-91, 0));
    }

    @Test
    void longitudeOutOfRangeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Location(0, 181));
        assertThrows(IllegalArgumentException.class, () -> new Location(0, -181));
    }

    @Test
    void nanIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Location(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Location(0, Double.NaN));
    }
}