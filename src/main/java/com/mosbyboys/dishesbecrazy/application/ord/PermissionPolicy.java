package com.mosbyboys.dishesbecrazy.application.ord;

import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.state.PlacedState;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.RestaurantOwner;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;

/* Checks if the logged-in user is allowed to do a command, based on their role
and whether the order/restaurant is theirs. */
@Component //@BEAN
public class PermissionPolicy {

    // Who can place orders
    // Only customers can place orders
    public void checkCanPlace(User caller) {
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if(caller.getRole() != Role.CUSTOMER){
            throw new PermissionDeniedException("Only a customer can place an order.");
        }
    }

    // Who can cancel and when
    // Cancel: a customer may cancel only while the order is still placed; afterwards cancellation is an operator action.
    public void checkCanCancel(User caller, Order order) { 
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if (order == null) {
            throw new PermissionDeniedException("No order.");
        }
        if(caller.getRole() == Role.OPERATOR){
            return; // operators can cancel at anytime
        }
        if(caller.getRole() == Role.COURIER || caller.getRole() == Role.RESTAURANT){
            throw new PermissionDeniedException("Only the customer or an operator can cancel an order.");
        }
        if(!(caller.getRole() == Role.CUSTOMER && order.getState() instanceof PlacedState)){
            throw new PermissionDeniedException("Customer can only cancel order before it is accepted.");
        }
        if(caller instanceof Customer customer){
            if(customer.getCustomerId().equals(order.getCustomerId())){
                return;
            }
        }
        throw new PermissionDeniedException("A customer can only cancel their own order, before it is accepted.");
    }

    // Check if the restaurant can make changes to an order
    // Only the restaurant that owns the order can make changes to it
    // accept, reject, startPreparing, markReady
    public void checkCanActAsRestaurant(User caller, Order order) { 
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if (order == null) {
            throw new PermissionDeniedException("No order.");
        }
        if(caller.getRole() != Role.RESTAURANT){
            throw new PermissionDeniedException("Only a restaurant owner can change order status.");
        }
        if(caller instanceof RestaurantOwner owner){
            if(owner.getRestaurantId().equals(order.getRestaurantId())){
                return;
            }
        }
        throw new PermissionDeniedException("Cannot change another restaurant's orders.");
    }

    // Check who can see the details of an order
    // Customers: Cannot see other customers' orders.
    // Restaurants: Cannot see other restaurants' orders
    public void checkCanView(User caller, Order order) { 
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if (order == null) {
            throw new PermissionDeniedException("No order.");
        }
        if(caller.getRole() == Role.OPERATOR){
            return; // operators can see everything
        }
        if(caller instanceof Customer customer){
            if(customer.getCustomerId().equals(order.getCustomerId())){
                return;
            }
        }
        if(caller instanceof RestaurantOwner restaurantOwner){
            if(order.getRestaurantId().equals(restaurantOwner.getRestaurantId())){
                return;
            }
        }
        if (caller.getRole() == Role.COURIER) {
            // TODO(DLV): allow only the courier assigned to this order.
            // Needs a courierId on Order, which doesn't exist until DLV is implemented.
            throw new PermissionDeniedException("Couriers cannot view orders yet (DLV not implemented).");
        }
        throw new PermissionDeniedException("Cannot view this order.");
    }
}
