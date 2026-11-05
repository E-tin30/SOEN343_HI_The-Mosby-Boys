package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.Map;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;

/* An order as the history page needs it: the order itself and the names of its
menu items (order lines only store ids). */
public record OrderHistoryView(Order order, Map<UUID, String> itemNames) {}