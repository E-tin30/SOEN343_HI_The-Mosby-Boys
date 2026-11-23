package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.PlacedQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.PreparationQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.application.dlv.events.OrderReadyEvent;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderObserver;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Consumer;

/* Runs the ORD use cases (place, accept, reject, prepare, ready, cancel).
Every command: load, check permission, change the order's state, save it, record an event,
and only then change the queues (they live in memory, so a rollback can't undo them).
No per-request data in fields, Spring only makes one of these. (Singleton) */
@Service
@RequiredArgsConstructor
public class ORDFacade {

    private final OrderService orderService;
    private final List<OrderObserver> subscribers = new CopyOnWriteArrayList<>();
    private final PermissionPolicy permissionPolicy;
    private final OrderQueues queues;
    private final MenuService menuService;
    private final RestaurantService restaurantService;
    private final OrderQueries orderQueries;
    private final REGFacade regFacade;
    private final ApplicationEventPublisher eventPublisher;

    public void subscribe(OrderObserver observer) {
        subscribers.add(observer);
    }

    public void unsubscribe(OrderObserver observer) {
        subscribers.remove(observer);
    }

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

    @Transactional
    public List<RestaurantOrderView> getRestaurantOrders(User user) {
        return orderQueries.getRestaurantOrders(user);
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
        for (OrderLine item : orderLineItems) {
            subtotal = subtotal.add(item.getLineTotal());
        }
        // deduct stock first: if there isn't enough stock, this throws before anything is saved
        // Sorted by item id so concurrent orders always lock menu items in the same order.
        orderLineItems.stream()
                .sorted(Comparator.comparing(OrderLine::getMenuItemId))
                .forEach(this::reserveStock);

        Order order = new Order(customer.getUserId(), restaurantId, orderLineItems, subtotal);

        orderService.save(order);
        // record transition change event
        publishTransition(order, null, user.getUserId(), "PLACE");
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
    public Order getOrder(UUID orderId) {
        return orderService.load(orderId);
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

        applyTransition(order, user, Order::accept, "ACCEPT");
        // placed queue -> preparation queue (FIFO)
        line.dequeueIfFirst(orderId);
        queues.preparation(order.getRestaurantId()).enqueue(orderId);
    }

    @Transactional
    public void rejectOrder(UUID userId, UUID orderId, String reason) {
        User user = regFacade.getCurrentUser(userId);
        Order order = orderService.loadForUpdate(orderId);
        permissionPolicy.checkCanActAsRestaurant(user, order);
        applyTransition(order, user, o -> o.reject(reason), "REJECT");
        restoreStock(order);
        // remove from placed queue
        queues.placed(order.getRestaurantId()).remove(orderId);
    }

    @Transactional
    public void cancelOrder(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
        Order order = orderService.loadForUpdate(orderId);
        permissionPolicy.checkCanCancel(user, order);
        boolean foodHasLeft = order.foodHasLeftRestaurant();
        applyTransition(order, user, Order::cancel, "CANCEL");
        if (!foodHasLeft) {
            restoreStock(order);
        }
        // the order could be in any line. Remove returns false where it isn't
        UUID restaurantId = order.getRestaurantId();
        queues.placed(restaurantId).remove(orderId);
        queues.preparation(restaurantId).remove(orderId);
        queues.ready(restaurantId).remove(orderId);
        queues.removeGlobalReady(orderId);
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

        applyTransition(order, user, Order::startPreparing, "PREPARING");
        // get next in line (FIFO)
        line.dequeueIfFirst(orderId);
    }

    @Transactional
    public void markOrderReady(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanActAsRestaurant(user, order);
        applyTransition(order, user, Order::markReady, "READY");

        //Enqueue the order in both the individual restaurant queue and global ready queue
        queues.ready(order.getRestaurantId()).enqueue(orderId);
        queues.enqueueGlobalReady(orderId);

        //Notify the dispatch system to find a courier
        eventPublisher.publishEvent(new OrderReadyEvent());
    }

    @Transactional
    public void pickUpOrder(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanActAsCourier(user, order);
        applyTransition(order, user, Order::pickUp, "PICKUP");
    }

    @Transactional
    public void deliverOrder(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanActAsCourier(user, order);
        applyTransition(order, user, Order::deliver, "DELIVER");
    }

    @Transactional
    public void refundOrder(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanRefund(user, order);
        applyTransition(order, user, Order::refund, "REFUND");
    }

    @Transactional
    public List<OrderHistoryView> getOrderHistory(User user) {
        return orderQueries.getOrderHistory(user);
    }

    @Transactional
    public List<OrderHistoryView> getCourierCurrentOrders(User user) {
        return orderQueries.getCourierCurrentOrders(user);
    }

    // Every transition saves first, then publishes exactly one event.
    private void applyTransition(Order order, User user, Consumer<Order> change, String action) {
        String before = order.getState().toString();
        change.accept(order);
        orderService.save(order);
        publishTransition(order, before, user.getUserId(), action);
    }

    // Builds the event for one transition and hands it to every subscriber.
    private void publishTransition(Order order, String before, UUID performedBy, String action) {
        notifySubscribers(new OrderStateChanged(
                order.getId(), before, order.getState().toString(), performedBy, action));
    }

    // The template's notifySubscribers: calls update(context) on each subscriber in turn.
    private void notifySubscribers(OrderStateChanged event) {
        for (OrderObserver subscriber : subscribers) {
            subscriber.update(event);
        }
    }
    
    @Transactional
    public Restaurant openRestaurant(UUID userId, UUID restaurantId) {
        Restaurant restaurant = restaurantService.load(restaurantId);
        User user = regFacade.getCurrentUser(userId);
        permissionPolicy.checkCanOpen(user, restaurant);
        restaurant.open();
        restaurantService.save(restaurant);
        return restaurant;
    }

    @Transactional
    public Restaurant pauseRestaurant(UUID userId, UUID restaurantId) {
        Restaurant restaurant = restaurantService.load(restaurantId);
        User user = regFacade.getCurrentUser(userId);
        permissionPolicy.checkCanPause(user, restaurant);
        restaurant.pause();
        restaurantService.save(restaurant);
        return restaurant;
    }

    @Transactional
    public Restaurant closeRestaurant(UUID userId, UUID restaurantId) {
        Restaurant restaurant = restaurantService.load(restaurantId);
        User user = regFacade.getCurrentUser(userId);
        permissionPolicy.checkCanClose(user, restaurant);
        restaurant.close();
        restaurantService.save(restaurant);
        return restaurant;
    }

    private void reserveStock(OrderLine line) {
        MenuItem item = menuService.loadForUpdate(line.getMenuItemId());
        item.decreaseStock(line.getQuantity());
        menuService.save(item);
    }

    // Puts an order's quantities back on the menu stock (order was rejected or cancelled before the food left).
    private void restoreStock(Order order) {
        List<OrderLine> lines = order.getOrderLineItems().stream()
                .sorted(Comparator.comparing(OrderLine::getMenuItemId))
                .toList();
        for (OrderLine line : lines) {
            try {
                MenuItem item = menuService.loadForUpdate(line.getMenuItemId());
                item.increaseStock(line.getQuantity());
                menuService.save(item);
            } catch (NoSuchElementException e) {
                // the item was removed from the menu; there is no stock to give back
            }
        }
    }

    //save menu item
    @Transactional
    public void saveMenuItem(MenuItem item) {
        menuService.save(item);
    }

    //get restaurantID from db
    @Transactional
    public UUID getRestaurantId(UUID menuItemId) {
        return menuService.getRestaurantId(menuItemId);
    }

}