package com.mosbyboys.dishesbecrazy.application.reg;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;

/* Turns a street address into coordinates. */
public interface Geocoder {
    Location geocode(String address);
}
