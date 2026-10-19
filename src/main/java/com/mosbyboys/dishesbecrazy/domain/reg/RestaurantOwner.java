package com.mosbyboys.dishesbecrazy.domain.reg;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;

// Owner/staff profile plus its linked Restaurant row; userId is Restaurant.user_id.
@Getter
public final class RestaurantOwner extends User {
    private final UUID restaurantId;
    private final OffsetDateTime restaurantCreatedAt;
    private final String name;
    private final String status;

    public RestaurantOwner(UUID userId, OffsetDateTime createdAt, String firstName, String lastName, String email,
                    String username, String hashedPassword,
                    UUID restaurantId, OffsetDateTime restaurantCreatedAt, String name, String status) {
        // The subclass fixes one role; callers cannot supply a conflicting role.
        super(userId, createdAt, firstName, lastName, email, username, hashedPassword, Role.RESTAURANT);
        this.restaurantId = restaurantId;
        this.restaurantCreatedAt = restaurantCreatedAt;
        this.name = name;
        this.status = status;
    }

}
