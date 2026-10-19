package com.mosbyboys.dishesbecrazy.domain.reg;

import java.time.OffsetDateTime;
import java.util.UUID;

// An operator has only User fields; Supabase has no separate operator table.
public final class Operator extends User {
    public Operator(UUID userId, OffsetDateTime createdAt, String firstName, String lastName, String email,
                    String username, String hashedPassword) {
        // The subclass fixes one role; callers cannot supply a conflicting role.
        super(userId, createdAt, firstName, lastName, email, username, hashedPassword, Role.OPERATOR);
    }
}
