package com.mosbyboys.dishesbecrazy.application.dash;

import com.mosbyboys.dishesbecrazy.application.dash.RestaurantDashboardData;
import com.mosbyboys.dishesbecrazy.application.ord.RestaurantService;
import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RestaurantService restaurantService;
    private final OrderQueues orderQueues;
    private final DLVFacade dlvFacade;

    public List<RestaurantDashboardData> getRestaurants() {
        //Note: The list being returned is unmodfiable since we are returning it with .toList() 

        return restaurantService.findAll().stream()
            .map(r -> {
                UUID restaurantId = r.getUserId();
                int queueLength = orderQueues.preparation(restaurantId).size();
                return new RestaurantDashboardData(restaurantId, r.getName(), r.getStatus(), r.getLocation().latitude(), r.getLocation().longitude(), queueLength);
            })
            .toList();
    }

    public List<Courier> getCouriers(){
        return dlvFacade.getOnDutyCouriers();
    }
}

