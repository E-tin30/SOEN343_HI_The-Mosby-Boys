package com.mosbyboys.dishesbecrazy.domain.reg;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;

import lombok.Getter;

// Courier state stays text, matching Supabase; location is lat/long, userId is Courier.user_id.
@Getter
public final class Courier extends User {
    private final UUID courierId;
    private final OffsetDateTime courierCreatedAt;
    private final String state;
    private final Location location;

    public Courier(UUID userId, OffsetDateTime createdAt, String firstName, String lastName, String email,
                    String username, String hashedPassword,
                    UUID courierId, OffsetDateTime courierCreatedAt, String state, Location location) {
        // The subclass fixes one role; callers cannot supply a conflicting role.
        super(userId, createdAt, firstName, lastName, email, username, hashedPassword, Role.COURIER);
        this.courierId = courierId;
        this.courierCreatedAt = courierCreatedAt;
        this.state = state;
        this.location = location;
    }

}
