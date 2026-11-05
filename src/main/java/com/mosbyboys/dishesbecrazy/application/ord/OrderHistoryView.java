package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.Map;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;

/* An order as the history page needs it: the order itself, the names of its menu items
(order lines only store ids), and the restaurant's and customer's names.
customerNames is empty unless the caller is an operator or restaurant. */
public record OrderHistoryView(Order order, Map<UUID, String> itemNames,
                               Map<UUID, String> restaurantNames, Map<UUID, String> customerNames) {}