package com.mosbyboys.dishesbecrazy.domain.reg;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;

// Shared attributes from Supabase Users; userId maps to Users.id. Only a concrete role can be instantiated.
@Getter
public abstract class User {
    private final UUID userId;
    private final OffsetDateTime createdAt;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String username;

    // Holds an already-hashed value, never a raw password; no public getter exposes it.
    @Getter(AccessLevel.NONE)
    private final String hashedPassword;
    private final Role role;

    // Explicit constructor avoids IDE issues with Lombok-generated constructors on Java 21.
    protected User(UUID userId, OffsetDateTime createdAt, String firstName, String lastName,
                   String email, String username, String hashedPassword, Role role) {
        this.userId = userId;
        this.createdAt = createdAt;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.username = username;
        this.hashedPassword = hashedPassword;
        this.role = role;
    }
}
