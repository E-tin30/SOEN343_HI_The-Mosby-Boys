package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.Map;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;

/* An order as the history page needs it: the order itself, the names of its menu items
(order lines only store ids), the restaurant's and customer's names, and the price breakdown.
customerNames is empty unless the caller is an operator or restaurant; pricing is null unless
the caller is a customer or operator. */
public record OrderHistoryView(Order order, Map<UUID, String> itemNames, Map<UUID, 
    String> restaurantNames, Map<UUID, String> customerNames, PricingBreakdown pricing) {}