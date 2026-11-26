package com.mosbyboys.dishesbecrazy.application.ntf;

import java.util.UUID;

// The order number people see in the UI: "#" + the last 6 characters of the id (same as order.id.slice(-6)).
final class OrderNumber {

    private OrderNumber() {
    }

    static String of(UUID orderId) {
        String id = orderId.toString();
        return "#" + id.substring(id.length() - 6);
    }
}