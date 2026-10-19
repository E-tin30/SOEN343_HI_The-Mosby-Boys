package com.mosbyboys.dishesbecrazy.domain.reg;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

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
                    data.profileId(), data.profileCreatedAt(), data.address());
            case COURIER -> new Courier(
                    data.userId(), data.createdAt(), data.firstName(), data.lastName(),
                    data.email(), data.username(), data.hashedPassword(),
                    data.profileId(), data.profileCreatedAt(), data.state(), data.location());
            case RESTAURANT -> new RestaurantOwner(
                    data.userId(), data.createdAt(), data.firstName(), data.lastName(),
                    data.email(), data.username(), data.hashedPassword(),
                    data.profileId(), data.profileCreatedAt(), data.restaurantName(), data.status(), data.location());
            case OPERATOR -> new Operator(
                    data.userId(), data.createdAt(), data.firstName(), data.lastName(),
                    data.email(), data.username(), data.hashedPassword());
        };
    }

    /**
     * Carries the information that UserFactory needs to create a User subclass.
     * Holds shared fields (such as name and email) and role-specific fields (such as
     * customer address or courier state). The factory reads the fields for the selected role.
     * profileId/profileCreatedAt identify the customer, courier, or restaurant profile;
     * operators do not use them.
     */
    @Builder
    public record UserData(UUID userId, OffsetDateTime createdAt,
                           String firstName, String lastName, String email, String username,
                           String hashedPassword, UUID profileId, OffsetDateTime profileCreatedAt,
                           String address, String state, String location,
                           String restaurantName, String status) {
        // Avoid the record's default output, which would expose the password hash.
        @Override
        public String toString() {
            return "UserData[userId=" + userId + ", profileId=" + profileId + "]";
        }

        public static class UserDataBuilder {
            @Override
            public String toString() {
                return "UserDataBuilder";
            }
        }
    }
}
