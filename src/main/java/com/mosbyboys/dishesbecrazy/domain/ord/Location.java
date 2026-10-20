package com.mosbyboys.dishesbecrazy.domain.ord;

import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class Location {
    private Double latitude;
    private Double longitude;

    protected Location() {
        // Default constructor for JPA
    }

    public Location(double latitude, double longitude) {
        if (!(latitude >= -90 && latitude <= 90)) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }
}