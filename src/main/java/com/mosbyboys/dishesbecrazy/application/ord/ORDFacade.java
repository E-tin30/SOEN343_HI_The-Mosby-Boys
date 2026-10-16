package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.technical.repository.MenuItemRepo;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/* Runs the ORD use cases (place, accept, reject, prepare, ready, cancel, get).
Every command: check permission, change the order's state, save it, record an event.
No per-request data in fields, Spring only makes one of these. (Singleton) */
@Service
@RequiredArgsConstructor
public class ORDFacade {

    private final OrderService orderService;
    private final OrderEventService orderEventService;
    private final PermissionPolicy permissionPolicy;
    private final MenuItemRepo menuItemRepo;   

    @Transactional
    public Order placeOrder(User user, UUID restaurantId, List<OrderLine> orderLineItems) {
        //Check Permission
        permissionPolicy.checkCanPlace(user);

        if(!(user instanceof Customer customer)){
            throw new PermissionDeniedException("Only a customer can place an order.");
        }

        Order order = new Order(user.getUserId(), restaurantId, orderLineItems);  
        order.getOrderLineItems().forEach(this::checkAgainstMenu);

        //save order to db
        orderService.save(order);

        return order;
    }

    private void checkAgainstMenu(OrderLine line) {
    MenuItem item = menuItemRepo.findById(line.getMenuItemId())
            .orElseThrow(() -> new IllegalArgumentException("Menu item not found: " + line.getMenuItemId()));
    if (item.getStatus() != MenuItemStatus.AVAILABLE) {
        throw new IllegalArgumentException("Item is sold out: " + item.getName());
    }
    if (line.getUnitPrice().compareTo(item.getPrice()) != 0) {
        throw new IllegalArgumentException("Price of " + item.getName() + " has changed, please refresh the menu");
    }
}

    @Transactional
    public void acceptOrder(User user, UUID orderId) {
        Order order = orderService.load(orderId);

        //Check Permission
        permissionPolicy.checkCanActAsRestaurant(user, order);

        String before = order.getState().toString();

        order.accept();

        //save state change event
        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "ACCEPT");
    }

    @Transactional
    public void rejectOrder(User user, UUID orderId, String reason) {
        Order order = orderService.load(orderId);

        //Check Permission
        permissionPolicy.checkCanActAsRestaurant(user, order);

        String before = order.getState().toString();

        order.reject(reason);

        //save to db
        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "REJECT");
    }

    @Transactional
    public void cancelOrder(User user, UUID orderId) {
        Order order = orderService.load(orderId);

        //Check Permission
        permissionPolicy.checkCanCancel(user, order);

        String before = order.getState().toString();

        order.cancel();

        //save to db
        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "CANCEL");
    }

    @Transactional
    public void prepareOrder(User user, UUID orderId) {
        Order order = orderService.load(orderId);

        //Check Permission
        permissionPolicy.checkCanActAsRestaurant(user, order);

        String before = order.getState().toString();

        order.startPreparing();

        //save to db
        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "PREPARING");
    }

    @Transactional
    public void markOrderReady(User user, UUID orderId) {
        Order order = orderService.load(orderId);

        //Check Permission
        permissionPolicy.checkCanActAsRestaurant(user, order);

        String before = order.getState().toString();

        order.markReady();

        //save to db
        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "READY");
    }

    /*other state transitions are out of scope for phase 2*/

}
