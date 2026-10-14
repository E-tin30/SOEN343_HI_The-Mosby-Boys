package com.mosbyboys.dishesbecrazy.domain.reg;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Getter;

// Customer fields mirror its Supabase row; inherited userId is Customer.user_id.
@Getter
public final class Customer extends User {
    private final UUID customerId;
    private final OffsetDateTime customerCreatedAt;
    private final String address;

    public Customer(UUID userId, OffsetDateTime createdAt, String firstName, String lastName, String email,
                    String username, String hashedPassword,
                    UUID customerId, OffsetDateTime customerCreatedAt, String address) {
        // The subclass fixes one role; callers cannot supply a conflicting role.
        super(userId, createdAt, firstName, lastName, email, username, hashedPassword, Role.CUSTOMER);
        this.customerId = customerId;
        this.customerCreatedAt = customerCreatedAt;
        this.address = address;
    }

}
