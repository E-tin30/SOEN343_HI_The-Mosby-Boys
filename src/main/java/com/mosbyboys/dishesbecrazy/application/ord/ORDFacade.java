package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;   // CHANGED: Spring's, not jakarta

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.PreparationQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.technical.repository.MenuItemRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.RestaurantRepo;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;

import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/* Runs the ORD use cases (place, accept, reject, prepare, ready, cancel).
Every command: load, check permission, change the order's state, save it, record an event,
and only then change the queues (they live in memory, so a rollback can't undo them).
No per-request data in fields, Spring only makes one of these. (Singleton) */
@Service
@RequiredArgsConstructor
public class ORDFacade {

    private final OrderService orderService;
    private final OrderEventService orderEventService;
    private final PermissionPolicy permissionPolicy;
    private final MenuItemRepo menuItemRepo;
    private final RestaurantRepo restaurantRepo;
    private final OrderQueues queues;
    private final MenuService menuService;
    private final RestaurantService restaurantService;
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
    public Order placeOrder(User user, UUID restaurantId, List<OrderLine> orderLineItems) {
        permissionPolicy.checkCanPlace(user);

        if (!(user instanceof Customer customer)) {
            throw new PermissionDeniedException("Only a customer can place an order.");
        }

        // no orders at a paused or closed restaurant
        Restaurant restaurant = restaurantRepo.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));
        if (!restaurant.isOpen()) {
            throw new IllegalArgumentException("Restaurant is not accepting orders");
        }

        Order order = new Order(customer.getCustomerId(), restaurantId, orderLineItems);
        order.getOrderLineItems().forEach(this::checkAgainstMenu);

        orderService.save(order);
        // record transition change event
        orderEventService.save(order.getId(), null, order.getState().toString(),
                user.getUserId(), "PLACE");
        // add to placed queue
        queues.placed(restaurantId).enqueue(order.getId());

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
    public void acceptOrder(User user, UUID orderId) {
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanActAsRestaurant(user, order);
        String before = order.getState().toString();

        order.accept();

        orderService.save(order);
        // record transition change event
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "ACCEPT");
        // placed queue -> preparation queue
        queues.placed(order.getRestaurantId()).remove(orderId);
        queues.preparation(order.getRestaurantId()).enqueue(orderId);
    }

    @Transactional
    public void rejectOrder(User user, UUID orderId, String reason) {
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanActAsRestaurant(user, order);
        String before = order.getState().toString();

        order.reject(reason);

        orderService.save(order);
        // record transition change event
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "REJECT");
        // remove from placed queue
        queues.placed(order.getRestaurantId()).remove(orderId);
    }

    @Transactional
    public void cancelOrder(User user, UUID orderId) {
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanCancel(user, order);
        String before = order.getState().toString();

        order.cancel();

        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "CANCEL");
        // the order could be in any line. Remove returns false where it isn't
        UUID restaurantId = order.getRestaurantId();
        queues.placed(restaurantId).remove(orderId);
        queues.preparation(restaurantId).remove(orderId);
        queues.ready(restaurantId).remove(orderId);
    }

    @Transactional
    public void prepareOrder(User user, UUID orderId) {
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanActAsRestaurant(user, order);

        // refuse anything that is not first in line, before changing anything
        PreparationQueue line = queues.preparation(order.getRestaurantId());
        if (!orderId.equals(line.peek())) {
            throw new IllegalStateException("Order " + orderId + " is not next in line");
        }

        String before = order.getState().toString();

        order.startPreparing();

        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "PREPARING");
        // get next in line (FIFO)
        line.dequeueIfFirst(orderId);
    }

    @Transactional
    public void markOrderReady(User user, UUID orderId) {
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanActAsRestaurant(user, order);
        String before = order.getState().toString();

        order.markReady();

        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "READY");
        // NEW
        queues.ready(order.getRestaurantId()).enqueue(orderId);
    }

    /* other state transitions are out of scope for phase 2 */
}