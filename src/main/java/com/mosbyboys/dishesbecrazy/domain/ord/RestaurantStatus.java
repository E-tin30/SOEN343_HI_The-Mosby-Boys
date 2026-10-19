package com.mosbyboys.dishesbecrazy.domain.ord;

/* OPEN takes orders; PAUSED keeps its menu but takes no new orders (UC-ORD-10); CLOSED is off the platform. */
public enum RestaurantStatus {
    OPEN,
    PAUSED,
    CLOSED
}
