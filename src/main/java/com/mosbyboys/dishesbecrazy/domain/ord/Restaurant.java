package com.mosbyboys.dishesbecrazy.domain.ord;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

@Getter
public class Restaurant {

    private final UUID id;
    private final UUID ownerId;
    private final String name;
    private final Location location;
    private final Instant createdAt;
    private RestaurantStatus status;

    public Restaurant(UUID id, UUID ownerId, String name, RestaurantStatus status, Location location, Instant createdAt) {
        if (id == null) {
            throw new IllegalArgumentException("Restaurant id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Restaurant name must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("Restaurant status must not be null");
        }
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.status = status;
        this.location = location;
        this.createdAt = createdAt;
    }

    public void pause() {
        this.status = RestaurantStatus.PAUSED;
    }

    public void reopen() {
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

    public boolean isOwnedBy(UUID userId) {
        return ownerId != null && ownerId.equals(userId);
    }
}
