package com.mosbyboys.dishesbecrazy.domain.ord;

import java.util.UUID;

public class ReadyQueue extends OrderQueue {
    
    public ReadyQueue(UUID restaurantId){
        super(restaurantId);
    }
}
