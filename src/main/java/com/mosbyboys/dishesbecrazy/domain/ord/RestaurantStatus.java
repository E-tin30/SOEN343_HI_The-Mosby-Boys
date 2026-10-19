package com.mosbyboys.dishesbecrazy.domain.ord;

import lombok.*;
import jakarta.persistence.*;

public enum RestaurantStatus {
    OPEN,
    PAUSED,
    CLOSED
}