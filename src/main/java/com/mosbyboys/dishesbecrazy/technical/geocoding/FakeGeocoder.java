package com.mosbyboys.dishesbecrazy.technical.geocoding;

import org.springframework.stereotype.Component;
import com.mosbyboys.dishesbecrazy.application.reg.Geocoder;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;

/* FAKE geocoder for demos: maps an address to a repeatable point inside Montreal.
   Not a real lookup. */
@Component
public class FakeGeocoder implements Geocoder {
    // Min and max to make sure that the location is not too far away and random but inside of a predefined square
    private static final double MIN_LAT = 45.42, MAX_LAT = 45.65;
    private static final double MIN_LON = -73.98, MAX_LON = -73.47;

    @Override
    public Location geocode(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }
        int h = address.trim().toLowerCase().hashCode();   // String.hashCode is fixed by the spec
        double a = (h & 0xFFFF) / 65535.0;
        double b = ((h >>> 16) & 0xFFFF) / 65535.0;
        return new Location(MIN_LAT + a * (MAX_LAT - MIN_LAT),
                            MIN_LON + b * (MAX_LON - MIN_LON));
    }
}
