package com.mosbyboys.dishesbecrazy.domain.ord;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.domain.reg.User;

import lombok.Getter;

// The restaurant account type: one account is one restaurant, so getUserId() is the restaurant's id.
@Getter
public class Restaurant extends User {

    private final String name;
    private final Location location;
    private RestaurantStatus status;

    public Restaurant(UUID userId, OffsetDateTime createdAt, String firstName, String lastName, String email,
                      String username, String hashedPassword,
                      String name, RestaurantStatus status, Location location) {
        // The subclass fixes one role; callers cannot supply a conflicting role.
        super(userId, createdAt, firstName, lastName, email, username, hashedPassword, Role.RESTAURANT);
        if (userId == null) {
            throw new IllegalArgumentException("Restaurant id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Restaurant name must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("Restaurant status must not be null");
        }
        this.name = name;
        this.status = status;
        this.location = location;
    }

    public void pause() {
        if (this.status == RestaurantStatus.CLOSED) {
            throw new IllegalStateException("Cannot pause a closed restaurant.");
        }
        this.status = RestaurantStatus.PAUSED;
    }

    public void open() {
        this.status = RestaurantStatus.OPEN;
    }

    public void close() {
        this.status = RestaurantStatus.CLOSED;
    }

    public boolean isOpen() {
        return this.status == RestaurantStatus.OPEN;
    }

    public boolean acceptsOrders() {
        return isOpen();
    }
}
