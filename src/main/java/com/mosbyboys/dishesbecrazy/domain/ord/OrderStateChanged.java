package com.mosbyboys.dishesbecrazy.domain.ord;

import java.util.UUID;

/* Published every time an order changes state. Observers react to it (R-ORD-05). */
public record OrderStateChanged(
        UUID orderId,
        String previousState,
        String newState,
        UUID performedBy,
        String action
) {}