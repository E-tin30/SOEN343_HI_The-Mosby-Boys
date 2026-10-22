package com.mosbyboys.dishesbecrazy.domain.reg;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;

import lombok.Getter;

// Customer fields mirror its Supabase row; inherited userId is Customer.user_id.
@Getter
public final class Customer extends User {
    private final OffsetDateTime customerCreatedAt;
    private final String address;
    private final Location location;

    public Customer(UUID userId, OffsetDateTime createdAt, String firstName, String lastName, String email,
                    String username, String hashedPassword, OffsetDateTime customerCreatedAt, String address, Location location) {
        // The subclass fixes one role; callers cannot supply a conflicting role.
        super(userId, createdAt, firstName, lastName, email, username, hashedPassword, Role.CUSTOMER);
        this.customerCreatedAt = customerCreatedAt;
        this.address = address;
        this.location = location;
    }
}
