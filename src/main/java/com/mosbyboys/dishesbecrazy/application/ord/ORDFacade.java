package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.application.prc.PricingService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.PlacedQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.PreparationQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;

import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final OrderQueues queues;
    private final MenuService menuService;
    private final RestaurantService restaurantService;
    private final PricingService  pricingService;
    private final REGFacade regFacade;

    @Transactional
    public List<Restaurant> getRestaurants(User user) {
        switch (user.getRole()) {
            case CUSTOMER -> {
                return restaurantService.findOpen();
            }
            case RESTAURANT-> {
                // A restaurant account is exactly one restaurant.
                return List.of(restaurantService.load(user.getUserId()));
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

    // States that still show on a restaurant's queue screen.
    private static final List<String> QUEUE_STATES =
            List.of("PlacedState", "AcceptedState", "PreparingState", "ReadyState");

    @Transactional
    public List<RestaurantOrderView> getRestaurantOrders(User user) {
        // A restaurant account is exactly one restaurant, so it only ever sees its own orders.
        if (!(user instanceof Restaurant restaurant)) {
            throw new PermissionDeniedException("Only a restaurant can view its order queues.");
        }
        UUID restaurantId = restaurant.getUserId();

        List<Order> orders = orderService.findForRestaurant(restaurantId, QUEUE_STATES);
        Map<UUID, String> itemNames = menuService.getNames(orders.stream()
                .flatMap(o -> o.getOrderLineItems().stream())
                .map(OrderLine::getMenuItemId)
                .collect(Collectors.toSet()));

        PlacedQueue placed = queues.placed(restaurantId);
        UUID nextAccept = placed.size() > 0 ? placed.peek() : null;
        PreparationQueue line = queues.preparation(restaurantId);
        UUID next = line.size() > 0 ? line.peek() : null;

        return orders.stream()
                .map(o -> new RestaurantOrderView(o, itemNames,
                        o.getId().equals(nextAccept), o.getId().equals(next)))
                .toList();
    }

    @Transactional
    public Order placeOrder(User user, UUID restaurantId, List<OrderLine> orderLineItems) {
        //Check Permission
        permissionPolicy.checkCanPlace(user);

        if (!(user instanceof Customer customer)) {
            throw new PermissionDeniedException("Only a customer can place an order.");
        }

        // no orders at a paused or closed restaurant
        Restaurant restaurant = restaurantService.load(restaurantId);
        if (!restaurant.isOpen()) {
            throw new IllegalArgumentException("Restaurant is not accepting orders");
        }

        orderLineItems.forEach(this::checkAgainstMenu);

        BigDecimal subtotal = BigDecimal.ZERO;
        orderLineItems.forEach(orderLineItem -> subtotal.add(orderLineItem.getLineTotal()));
        PricingBreakdown pricingBreakdown = pricingService.calculateTotal(subtotal);

        Order order = new Order(customer.getUserId(), restaurantId, orderLineItems, pricingBreakdown.total());

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
    public void acceptOrder(UUID userId, UUID orderId) {
        Order order = orderService.load(orderId);
        User user = regFacade.getCurrentUser(userId);

        //Check Permission
        permissionPolicy.checkCanActAsRestaurant(user, order);

        // refuse anything that is not first in line, before changing anything
        PlacedQueue line = queues.placed(order.getRestaurantId());
        if (!orderId.equals(line.peek())) {
            throw new IllegalStateException("Order " + orderId + " is not next in line");
        }

        String before = order.getState().toString();

        order.accept();

        orderService.save(order);
        // record transition change event
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "ACCEPT");
        // placed queue -> preparation queue (FIFO)
        line.dequeueIfFirst(orderId);
        queues.preparation(order.getRestaurantId()).enqueue(orderId);
    }

    @Transactional
    public void rejectOrder(UUID userId, UUID orderId, String reason) {
        User user = regFacade.getCurrentUser(userId);
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
    public void cancelOrder(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
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
    public void prepareOrder(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
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
    public void markOrderReady(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanActAsRestaurant(user, order);
        String before = order.getState().toString();

        order.markReady();

        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), user.getUserId(), "READY");
        queues.ready(order.getRestaurantId()).enqueue(orderId);
    }

    /* other state transitions are out of scope for phase 2 */
}