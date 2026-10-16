package com.mosbyboys.dishesbecrazy.domain.ord;

import lombok.*;
import jakarta.persistence.*;

@Embeddable
@AllArgsConstructor
@Getter
@Setter
public class Location {
    private Double latitude;
    private Double longitude;

    protected Location() {
        // Default constructor for JPA
    }
}