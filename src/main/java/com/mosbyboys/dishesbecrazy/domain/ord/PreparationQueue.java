package com.mosbyboys.dishesbecrazy.domain.ord;

import java.util.UUID;

/* One restaurant's accepted orders in arrival order (FIFO, R-ORD-03).
Only the order at the front can start preparing. 
Order leaves the queue when order changes from accepted to startPreparing */
public class PreparationQueue extends OrderQueue {

    public PreparationQueue(UUID restaurantId){
        super(restaurantId);
    }
}
