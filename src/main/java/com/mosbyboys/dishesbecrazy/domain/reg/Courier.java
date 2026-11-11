package com.mosbyboys.dishesbecrazy.domain.reg;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;

import lombok.Getter;

// location is lat/long; userId is also Courier's primary key (user_id).
@Getter
public final class Courier extends User {
    private final CourierState state;
    private final Location location;

    public Courier(UUID userId, OffsetDateTime createdAt, String firstName, String lastName, String email,
                    String username, String hashedPassword, CourierState state, Location location) {
        // The subclass fixes one role; callers cannot supply a conflicting role.
        super(userId, createdAt, firstName, lastName, email, username, hashedPassword, Role.COURIER);
        this.state = state;
        this.location = location;
    }

}
