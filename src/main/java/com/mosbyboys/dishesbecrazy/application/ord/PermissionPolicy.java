package com.mosbyboys.dishesbecrazy.application.ord;

/* Checks if the logged-in user is allowed to do a command, based on their role
and whether the order/restaurant is theirs. */
public class PermissionPolicy {

    public void checkCanPlace(User caller) {

    }

    public void checkCanCancel(User caller, Order order) { 

    }

    // accept, reject, startPreparing, markReady
    public void checkCanActAsRestaurant(User caller, Order order) { 

    }

    public void checkCanView(User caller, Order order) { 

    }
}
