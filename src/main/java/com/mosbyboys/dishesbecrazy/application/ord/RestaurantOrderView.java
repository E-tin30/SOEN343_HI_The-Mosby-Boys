package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.Map;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;

/* An order as the restaurant's queue screen needs it: the order itself, the names of its
menu items (order lines only store ids), and whether it is first in the placed line
(next to accept) or the preparation line (next to prepare). */
public record RestaurantOrderView(Order order, Map<UUID, String> itemNames,
                                  boolean nextToAccept, boolean nextToPrepare) {}
