package com.mosbyboys.dishesbecrazy.domain.ord;

import java.util.UUID;

// orders placed to a restaurant waiting to be accepted or rejected. OrderState = Placed
public class PlacedQueue extends OrderQueue {
    
    public PlacedQueue(UUID restaurantId){
        super(restaurantId);
    }

}
