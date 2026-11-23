package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.application.prc.PricingService;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderQueue;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.domain.reg.User;

import lombok.RequiredArgsConstructor;

// Read-only; ORDFacade owns the transactions.
@Service
@RequiredArgsConstructor
public class OrderQueries {

    private final OrderService orderService;
    private final OrderQueues queues;
    private final MenuService menuService;
    private final RestaurantService restaurantService;
    private final PricingService pricingService;
    private final REGFacade regFacade;

    private static final List<String> QUEUE_STATES =
            List.of("PlacedState", "AcceptedState", "PreparingState", "ReadyState");

    public List<RestaurantOrderView> getRestaurantOrders(User user) {
        if (!(user instanceof Restaurant restaurant)) {
            throw new PermissionDeniedException("Only a restaurant can view its order queues.");
        }
        UUID restaurantId = restaurant.getUserId();

        List<Order> orders = orderService.findForRestaurant(restaurantId, QUEUE_STATES);
        Map<UUID, String> itemNames = itemNamesOf(orders);
        UUID nextToAccept = headOf(queues.placed(restaurantId));
        UUID nextToPrepare = headOf(queues.preparation(restaurantId));

        return orders.stream()
                .map(o -> new RestaurantOrderView(o, itemNames,
                        o.getId().equals(nextToAccept), o.getId().equals(nextToPrepare)))
                .toList();
    }

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

    // History leaves out READY orders, so the courier dashboard reads these instead.
    public List<OrderHistoryView> getCourierCurrentOrders(User user) {
        if (user.getRole() != Role.COURIER) {
            throw new PermissionDeniedException("Only a courier has current deliveries.");
        }
        return toViews(user, orderService.findCurrentForCourier(user.getUserId()));
    }

    private List<OrderHistoryView> toViews(User user, List<Order> orders) {
        Map<UUID, String> itemNames = itemNamesOf(orders);
        Map<UUID, String> restaurantNames = restaurantService.getNames(orders.stream()
                .map(Order::getRestaurantId)
                .collect(Collectors.toSet()));

        boolean seesCustomers = user.getRole() == Role.OPERATOR || user.getRole() == Role.RESTAURANT;
        Map<UUID, String> customerNames = seesCustomers
            ? regFacade.getDisplayNames(orders.stream()
                    .map(Order::getCustomerId)
                    .collect(Collectors.toSet()))
            : Map.of();

        return orders.stream()
            .map(o -> new OrderHistoryView(o, itemNames, restaurantNames, customerNames, priceOf(o)))
            .toList();
    }

    private Map<UUID, String> itemNamesOf(List<Order> orders) {
        return menuService.getNames(orders.stream()
                .flatMap(o -> o.getOrderLineItems().stream())
                .map(OrderLine::getMenuItemId)
                .collect(Collectors.toSet()));
    }

    private UUID headOf(OrderQueue queue) {
        return queue.size() > 0 ? queue.peek() : null;
    }

    // Priced with today's rates, not the rates at order time.
    private PricingBreakdown priceOf(Order order) {
        if (order.getSubtotal() == null) {
            return null;
        }
        return pricingService.calculateTotal(order.getSubtotal());
    }
}
