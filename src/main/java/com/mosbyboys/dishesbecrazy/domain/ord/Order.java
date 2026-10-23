package com.mosbyboys.dishesbecrazy.domain.ord;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.PlacedState;

import lombok.Getter;

/* One customer's order at one restaurant. Holds its lines and current state,
and passes accept/reject/etc... to the state to check it's legal. No permissions or DB in here. */
@Getter 
public class Order {
    
    private final UUID id;
    private OrderState state;
    // Both are User ids: the customer's, and the restaurant owner's (which is the restaurant's id).
    private final UUID customerId;
    private final UUID restaurantId;
    private final List<OrderLine> orderLineItems;
    private final Instant placedAt;
    private String rejectionReason;

    public Order(UUID customerId, UUID restaurantId, List<OrderLine> orderLineItems){
        if(orderLineItems == null || orderLineItems.isEmpty()){
            throw new IllegalArgumentException("orderLineItems must not be null or empty");
        }
        
        if(customerId == null){
            throw new IllegalArgumentException("customerId must not be null");
        }

        if(restaurantId == null){
            throw new IllegalArgumentException("restaurantId must not be null");
        }

        this.id = UUID.randomUUID();
        this.state = new PlacedState();
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.orderLineItems = List.copyOf(orderLineItems);
        this.placedAt = Instant.now();
    }

    //The private Order constructor is for the reconstitute method so it can bypass the final attributes
    private Order(UUID id, OrderState state, UUID customerId, UUID restaurantId,
                  List<OrderLine> lines, Instant placedAt, String rejectionReason) {
        this.id = id;
        this.state = state;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.orderLineItems = List.copyOf(lines);
        this.placedAt = placedAt;
        this.rejectionReason = rejectionReason;
    }

    //This method is for loading the objects back from the db
    // For the repo only
    public static Order reconstitute(UUID id, OrderState state, UUID customerId, UUID restaurantId,
                                     List<OrderLine> lines, Instant placedAt, String rejectionReason) {
        if(state == null){
            throw new IllegalArgumentException("State cannot be null");
        }
        if(lines == null){
            throw new IllegalArgumentException("Order Lines cannot be null");
        }
        return new Order(id, state, customerId, restaurantId, lines, placedAt, rejectionReason);
    }

    // State changes
    public void accept(){
        this.state = this.state.accept();
    }

    public void reject(String rejectionReason){
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        this.state = this.state.reject();
        this.rejectionReason = rejectionReason;
    }

    public void cancel(){
        this.state = this.state.cancel();
    }

    public void startPreparing(){
        this.state = this.state.startPreparing();
    }

    public void markReady(){
        this.state = this.state.markReady();
    }

    public BigDecimal getTotal(){
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine orderLine : orderLineItems) {
            total = total.add(orderLine.getLineTotal());
        }
        return total;
    }
    
}
