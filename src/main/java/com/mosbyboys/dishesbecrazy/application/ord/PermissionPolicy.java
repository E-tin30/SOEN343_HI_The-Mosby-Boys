package com.mosbyboys.dishesbecrazy.application.ord;

import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.state.DeliveredState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.PlacedState;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.domain.reg.Courier;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
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
            if(customer.getUserId().equals(order.getCustomerId())){
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
        if(caller instanceof Restaurant restaurant){
            if(restaurant.getUserId().equals(order.getRestaurantId())){
                return;
            }
        }
        throw new PermissionDeniedException("Cannot change another restaurant's orders.");
    }

    // Accept or decline delivery offer: the order must be offered to this courier, and they must not have accepted yet.
    public void checkCanRespondToOffer(User caller, Order order) {
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if (order == null) {
            throw new PermissionDeniedException("No order.");
        }
        if (caller.getRole() != Role.COURIER || !(caller instanceof Courier courier)) {
            throw new PermissionDeniedException("Only a courier can accept or decline a delivery offer.");
        }
        if (!courier.getUserId().equals(order.getCourierId())) {
            throw new PermissionDeniedException("This order was not offered to you.");
        }
        if (!"idle".equalsIgnoreCase(courier.getState())) {
            throw new PermissionDeniedException("There is no pending offer for you on this order.");
        }
    }

    // Pick up or deliver: the order must be this courier's AND they must already have accepted it.
    public void checkCanActAsCourier(User caller, Order order) {
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if (order == null) {
            throw new PermissionDeniedException("No order.");
        }
        if (caller.getRole() != Role.COURIER || !(caller instanceof Courier courier)) {
            throw new PermissionDeniedException("Only a courier can pick up or deliver an order.");
        }
        if (!courier.getUserId().equals(order.getCourierId())) {
            throw new PermissionDeniedException("Only the courier assigned to this order can change it.");
        }
        if (!"assigned".equalsIgnoreCase(courier.getState()) && !"carrying".equalsIgnoreCase(courier.getState())) {
            throw new PermissionDeniedException("You have not accepted this order, or you are not on duty.");
        }
    }

    // Check if the user is allowed to refund the order
    // Only operators can do so
    public void checkCanRefund(User caller, Order order) {
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if (order == null) {
            throw new PermissionDeniedException("No order.");
        }
        if (caller.getRole() != Role.OPERATOR){ 
            throw new PermissionDeniedException("Only an operator can refund an order.");
        }
        if (!(order.getState() instanceof DeliveredState)){
            throw new PermissionDeniedException("Only a delivered order can be refunded.");
        }
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
            if(customer.getUserId().equals(order.getCustomerId())){
                return;
            }
        }
        if(caller instanceof Restaurant restaurant){
            if(order.getRestaurantId().equals(restaurant.getUserId())){
                return;
            }
        }
        if (caller.getRole() == Role.COURIER) {
            if (caller.getUserId().equals(order.getCourierId())) {
                return;
            }
            throw new PermissionDeniedException("A courier can only view the order assigned to them.");
        }
        throw new PermissionDeniedException("Cannot view this order.");
    }

    public void checkCanBrowseMenu(User caller, Restaurant restaurant) {
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if (restaurant == null) {
            throw new PermissionDeniedException("No restaurant.");
        }
          switch (caller.getRole()) {
        case OPERATOR -> {
             return;
             }  
        case RESTAURANT -> {
            if (restaurant.getUserId().equals(caller.getUserId())) return;
            throw new PermissionDeniedException("Cannot view another restaurant's menu.");
        }
        case CUSTOMER -> {
            if (restaurant.isOpen()) return;
            throw new PermissionDeniedException("This restaurant is paused and not taking orders.");
        }
        case COURIER -> throw new PermissionDeniedException("Couriers cannot view restaurant menus yet (DLV not implemented).");
        }
    }

    // Only an operator can replace the courier of an order (R-DLV-02).
    public void checkCanReassign(User caller, Order order) {
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if (order == null) {
            throw new PermissionDeniedException("No order.");
        }
        if (caller.getRole() != Role.OPERATOR) {
            throw new PermissionDeniedException("Only an operator can reassign an order.");
        }
    }

    // Who can pause a restaurant
    // An operator can pause any restaurant; a restaurant can only pause itself (Appendix A, R-DASH-01).
    public void checkCanPause(User caller, Restaurant restaurant) {
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if (restaurant == null) {
            throw new PermissionDeniedException("No restaurant.");
        }
        if (caller.getRole() == Role.OPERATOR) {
            return;
        }
        if (caller.getRole() == Role.RESTAURANT && caller.getUserId().equals(restaurant.getUserId())) {
            return;
        }
        throw new PermissionDeniedException("Only an operator or the restaurant itself can pause a restaurant.");
    }

    // Who can open a restaurant (from paused or closed)
    // An operator can open any restaurant (R-DASH-01); a restaurant can only open itself
    // (team decision: the restaurant knows when it has caught up after a pause).
    public void checkCanOpen(User caller, Restaurant restaurant) {
        if (caller == null) {
            throw new PermissionDeniedException("No caller.");
        }
        if (restaurant == null) {
            throw new PermissionDeniedException("No restaurant.");
        }
        if (caller.getRole() == Role.OPERATOR) {
            return;
        }
        if (caller.getRole() == Role.RESTAURANT && caller.getUserId().equals(restaurant.getUserId())) {
            return;
        }
        throw new PermissionDeniedException("Only an operator or the restaurant itself can open a restaurant.");
    }
}