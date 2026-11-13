package com.mosbyboys.dishesbecrazy.domain.ord;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.PlacedState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.ReadyState;

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
    private UUID courierId;
    private final List<OrderLine> orderLineItems;
    private final Instant placedAt;
    private String rejectionReason;
    private final BigDecimal subtotal;

    public Order(UUID customerId, UUID restaurantId, List<OrderLine> orderLineItems, BigDecimal subtotal){
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
        this.courierId = null;
        this.orderLineItems = List.copyOf(orderLineItems);
        this.placedAt = Instant.now();
        this.subtotal = subtotal;
    }

    //The private Order constructor is for the reconstitute method so it can bypass the final attributes
    private Order(UUID id, OrderState state, UUID customerId, UUID restaurantId, UUID courierId,
                  List<OrderLine> lines, Instant placedAt, String rejectionReason, BigDecimal subtotal) {
        this.id = id;
        this.state = state;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.courierId = courierId;
        this.orderLineItems = List.copyOf(lines);
        this.placedAt = placedAt;
        this.rejectionReason = rejectionReason;
        this.subtotal = subtotal;
    }

    //This method is for loading the objects back from the db
    // For the repo only
    public static Order reconstitute(UUID id, OrderState state, UUID customerId, UUID restaurantId, UUID courierId,
                                     List<OrderLine> lines, Instant placedAt, String rejectionReason, BigDecimal subtotal) {
        if(state == null){
            throw new IllegalArgumentException("State cannot be null");
        }
        if(lines == null){
            throw new IllegalArgumentException("Order Lines cannot be null");
        }
        return new Order(id, state, customerId, restaurantId, courierId, lines, placedAt, rejectionReason, subtotal);
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

    public void pickUp() {
        this.state = this.state.pickUp();
    }

    public void deliver() {
        this.state = this.state.deliver();
    }

    public void refund() {
        this.state = this.state.refund();
    }

    public void assignCourier(UUID courierId) {
        if (courierId == null) {
            throw new IllegalArgumentException("courierId must not be null");
        }
        if (!(state instanceof ReadyState)) {
            throw new IllegalStateException("Only a ready order can be assigned a courier.");
        }
        this.courierId = courierId;
    }

    public BigDecimal getTotal(){
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine orderLine : orderLineItems) {
            total = total.add(orderLine.getLineTotal());
        }
        return total;
    }

    public void clearCourierOffer(UUID courierId) {
        if (!(state instanceof ReadyState) || !courierId.equals(this.courierId)) {
            throw new IllegalStateException("Courier does not hold this offer");
        }
        this.courierId = null;
    }
    
}
