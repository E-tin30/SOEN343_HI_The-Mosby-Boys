package com.mosbyboys.dishesbecrazy.domain.reg;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;

import lombok.Builder;

/** Simple Factory: selects the concrete User subclass from the account role. */
public final class UserFactory {
    public User createUser(Role role, UserData data) {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(data, "data must not be null");

        return switch (role) {
            case CUSTOMER -> new Customer(
                    data.userId(), data.createdAt(), data.firstName(), data.lastName(),
                    data.email(), data.username(), data.hashedPassword(),
                    data.address(), data.location());
            case COURIER -> new Courier(
                    data.userId(), data.createdAt(), data.firstName(), data.lastName(),
                    data.email(), data.username(), data.hashedPassword(),
                    data.state(), data.location());
            case RESTAURANT -> new Restaurant(
                    data.userId(), data.createdAt(), data.firstName(), data.lastName(),
                    data.email(), data.username(), data.hashedPassword(),
                    data.restaurantName(), data.status(), data.location());
            case OPERATOR -> new Operator(
                    data.userId(), data.createdAt(), data.firstName(), data.lastName(),
                    data.email(), data.username(), data.hashedPassword());
        };
    }

    /**
     * Carries the information that UserFactory needs to create a User subclass.
     * Holds shared fields (such as name and email) and role-specific fields (such as
     * customer address, courier state, or restaurant name). The factory reads the fields for the selected role.
     * userId is also the primary key of the customer, courier, or restaurant row.
     */
    @Builder
    public record UserData(UUID userId, OffsetDateTime createdAt,
                           String firstName, String lastName, String email, String username,
                           String hashedPassword, String address, CourierState state, Location location,
                           String restaurantName, RestaurantStatus status) {
        // Avoid the record's default output, which would expose the password hash.
        @Override
        public String toString() {
            return "UserData[userId=" + userId + "]";
        }

        public static class UserDataBuilder {
            @Override
            public String toString() {
                return "UserDataBuilder";
            }
        }
    }
}
