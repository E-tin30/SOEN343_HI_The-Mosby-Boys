package com.mosbyboys.dishesbecrazy.technical.geocoding;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;

class FakeGeocoderTest {

    private final FakeGeocoder geocoder = new FakeGeocoder();

    @Test
    void sameAddressGivesTheSamePoint() {
        Location first = geocoder.geocode("123 Demo Street");
        Location second = geocoder.geocode("123 Demo Street");

        assertEquals(first.getLatitude(), second.getLatitude());
        assertEquals(first.getLongitude(), second.getLongitude());
    }

    @Test
    void caseAndSurroundingSpacesDoNotMatter() {
        Location a = geocoder.geocode("123 Demo Street");
        Location b = geocoder.geocode("  123 DEMO street ");

        assertEquals(a.getLatitude(), b.getLatitude());
        assertEquals(a.getLongitude(), b.getLongitude());
    }

    @Test
    void differentAddressesGiveDifferentPoints() {
        Location a = geocoder.geocode("123 Demo Street");
        Location b = geocoder.geocode("456 Other Avenue");

        assertFalse(a.getLatitude().equals(b.getLatitude())
                && a.getLongitude().equals(b.getLongitude()));
    }

    @Test
    void resultIsInsideTheMontrealBox() {
        for (String address : new String[] {"1 A St", "22 B Ave", "333 C Blvd", "4444 D Rd", "x"}) {
            Location location = geocoder.geocode(address);

            assertTrue(location.getLatitude() >= 45.42 && location.getLatitude() <= 45.65, address);
            assertTrue(location.getLongitude() >= -73.98 && location.getLongitude() <= -73.47, address);
        }
    }

    @Test
    void blankOrNullAddressThrows() {
        assertThrows(IllegalArgumentException.class, () -> geocoder.geocode(null));
        assertThrows(IllegalArgumentException.class, () -> geocoder.geocode("   "));
    }
}