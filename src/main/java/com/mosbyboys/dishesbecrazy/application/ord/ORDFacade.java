package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.application.prc.PricingService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.PlacedQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.PreparationQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
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
    private final List<OrderObserver> subscribers = new CopyOnWriteArrayList<>();
    private final PermissionPolicy permissionPolicy;
    private final OrderQueues queues;
    private final MenuService menuService;
    private final RestaurantService restaurantService;
    private final PricingService  pricingService;
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
        for (OrderLine item : orderLineItems) {
            subtotal = subtotal.add(item.getLineTotal());
        }
        // deduct stock first: if there isn't enough stock, this throws before anything is saved
        orderLineItems.forEach(this::reserveStock);

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

        String before = order.getState().toString();

        order.accept();

        orderService.save(order);
        // record transition change event
        publishTransition(order, before, user.getUserId(), "ACCEPT");
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
        publishTransition(order, before, user.getUserId(), "REJECT");
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
        publishTransition(order, before, user.getUserId(), "CANCEL");
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

        String before = order.getState().toString();

        order.startPreparing();

        orderService.save(order);
        publishTransition(order, before, user.getUserId(), "PREPARING");
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
        publishTransition(order, before, user.getUserId(), "READY");

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
        String before = order.getState().toString();

        order.pickUp();

        orderService.save(order);
        publishTransition(order, before, user.getUserId(), "PICKUP");
    }

    @Transactional
    public void deliverOrder(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanActAsCourier(user, order);
        String before = order.getState().toString();

        order.deliver();

        orderService.save(order);
        publishTransition(order, before, user.getUserId(), "DELIVER");
    }

    @Transactional
    public void refundOrder(UUID userId, UUID orderId) {
        User user = regFacade.getCurrentUser(userId);
        Order order = orderService.load(orderId);
        permissionPolicy.checkCanRefund(user, order);
        String before = order.getState().toString();

        order.refund();

        orderService.save(order);
        publishTransition(order, before, user.getUserId(), "REFUND");
    }

    // Order history, scoped by the caller's role, newest first.
    @Transactional
    public List<OrderHistoryView> getOrderHistory(User user) {
        UUID id = user.getUserId();

        List<Order> orders = switch (user.getRole()) {
            case CUSTOMER   -> orderService.findHistoryForCustomer(id);
            case RESTAURANT -> orderService.findHistoryForRestaurant(id);
            case COURIER    -> orderService.findHistoryForCourier(id);
            case OPERATOR   -> orderService.findHistoryForAll();
        };

        return toViews(user, orders);
    }

    // A courier's orders that are still in their hands: offered or accepted (READY) or on the way (PICKEDUP)
    // History leaves READY orders out, so the courier dashboard reads these instead
    @Transactional
    public List<OrderHistoryView> getCourierCurrentOrders(User user) {
        if (user.getRole() != Role.COURIER) {
            throw new PermissionDeniedException("Only a courier has current deliveries.");
        }
        return toViews(user, orderService.findCurrentForCourier(user.getUserId()));
    }

    // Wraps orders with the item, restaurant and customer names and the pricing the caller's role may see
    private List<OrderHistoryView> toViews(User user, List<Order> orders) {
        // One query for every item name on the page, same as getRestaurantOrders.
        Map<UUID, String> itemNames = menuService.getNames(orders.stream()
                .flatMap(o -> o.getOrderLineItems().stream())
                .map(OrderLine::getMenuItemId)
                .collect(Collectors.toSet()));

        // Restaurant names, for every role, in one query.
        Map<UUID, String> restaurantNames = restaurantService.getNames(orders.stream()
                .map(Order::getRestaurantId)
                .collect(Collectors.toSet()));

        // An operator sees every customer's name; a restaurant sees the names of its own customers.
        boolean seesCustomers = user.getRole() == Role.OPERATOR || user.getRole() == Role.RESTAURANT;
        Map<UUID, String> customerNames = seesCustomers
            ? regFacade.getDisplayNames(orders.stream()
                    .map(Order::getCustomerId)
                    .collect(Collectors.toSet()))
            : Map.of();

        boolean seesPricing = user.getRole() == Role.CUSTOMER || user.getRole() == Role.OPERATOR;

        return orders.stream()
                .map(o -> new OrderHistoryView(o, itemNames, restaurantNames, customerNames, seesPricing ? priceOf(o) : null))
                .toList();
    }

    // The breakdown for one stored order, from its item subtotal and today's pricing rates.
    private PricingBreakdown priceOf(Order order) {
        if (order.getSubtotal() == null) {
            return null;
        }
        return pricingService.calculateTotal(order.getSubtotal());
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

    private void reserveStock(OrderLine line) {
        MenuItem item = menuService.load(line.getMenuItemId());
        item.decreaseStock(line.getQuantity());
        menuService.save(item);
    }

}