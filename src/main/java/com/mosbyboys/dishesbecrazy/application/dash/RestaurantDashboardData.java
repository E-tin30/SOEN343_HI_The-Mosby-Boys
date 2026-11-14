package com.mosbyboys.dishesbecrazy.application.dash;

import java.util.UUID;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;

public record RestaurantDashboardData(UUID id, String name, RestaurantStatus status, double latitude, double longitude, int queueLength){

}
