package com.mosbyboys.dishesbecrazy.application.prc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// One ledger row: a CHARGE or REFUND amount, plus the order's current (final) state and timestamps.
public record LedgerEntry(
        UUID orderId,
        String restaurantName,
        String entryType,
        BigDecimal amount,
        Instant recordedAt,
        Instant placedAt,
        String orderStatus) {
}
