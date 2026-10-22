package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;

import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
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
    private final MenuService menuService;
    private final RestaurantService restaurantService;
    private final UserRepo userRepo;
    private final REGFacade regFacade;

    @Transactional
    public List<Restaurant> getRestaurants(User user) {
        switch (user.getRole()) {
            case CUSTOMER -> {
                return restaurantService.findOpen();
            }
            case RESTAURANT-> {
                return restaurantService.findByOwner(user.getUserId());
            }
            case OPERATOR -> {
                return restaurantService.findAll();
            }
            case COURIER -> {
                throw new PermissionDeniedException("Couriers cannot view restaurants.");
            }
            default -> throw new IllegalArgumentException("Unknown role: " + user.getRole());
        }
    }

    @Transactional
    public List<MenuItem> getMenuForRestaurant(User user,UUID restaurantId) {
        Restaurant restaurant = restaurantService.load(restaurantId);   
        permissionPolicy.checkCanBrowseMenu(user, restaurant);        
        return menuService.getMenu(restaurantId); 
    }

    @Transactional
    public Order placeOrder(UUID userId, UUID restaurantId, List<OrderLine> orderLineItems) {
        User user = regFacade.getCurrentUser(userId);

        //Check Permission
        permissionPolicy.checkCanPlace(user);

        if (!(user instanceof Customer)) {
            throw new PermissionDeniedException("Only a customer can place an order.");
        }
        Order order = new Order(userId, restaurantId, orderLineItems);
        order.getOrderLineItems().forEach(this::checkAgainstMenu);

        //save order to db
        orderService.save(order);

        return order;
    }

    private void checkAgainstMenu(OrderLine line) {
        MenuItem item = menuService.load(line.getMenuItemId());
        if (!item.isAvailable()) {
            throw new IllegalArgumentException("Item is sold out: " + item.getName());
        }
        if (line.getUnitPrice().compareTo(item.getPrice()) != 0) {
            throw new IllegalArgumentException("Price of " + item.getName() + " has changed, please refresh the menu");
        }
    }

    @Transactional
    public void acceptOrder(UUID userId, UUID orderId) {
        Order order = orderService.load(orderId);
        User user = regFacade.getCurrentUser(userId);

        //Check Permission
        permissionPolicy.checkCanActAsRestaurant(user, order);

        String before = order.getState().toString();

        order.accept();

        //save state change event
        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "ACCEPT");
    }

    @Transactional
    public void rejectOrder(UUID userId, UUID orderId, String reason) {
        User user = regFacade.getCurrentUser(userId);
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
    public void cancelOrder(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
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
    public void prepareOrder(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
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
    public void markOrderReady(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
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