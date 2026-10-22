package com.mosbyboys.dishesbecrazy.ui.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        UUID userId,
        UUID restaurantId,
        List<OrderLineRequest> items
) {
    public record OrderLineRequest(
            UUID menuItemId,
            int quantity,
            BigDecimal price
    ) {}
}