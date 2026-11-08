package com.mosbyboys.dishesbecrazy.application.dlv;

import com.mosbyboys.dishesbecrazy.application.events.OrderOfferedEvent;
import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.application.ord.RestaurantService;


import java.util.UUID;
import java.util.List;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DispatchService {
    private final OrderQueues queues;
    private final OrderService orderService;
    private final CourierService courierService;
    private final RestaurantService restaurantService;
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchWaitingOrders() {
        for (UUID orderId : queues.getGlobalReady()) {

            Order order = orderService.load(orderId);

            // Skip orders already offered or assigned
            if (order.getCourierId() != null) {
                continue;
            }

            // Find the nearest available courier
            CourierData courier = findNearestIdleCourier(order);

            // No eligible courier available
            if (courier == null) {
                break;
            }

            // Offer order to the selected courier
            offerOrder(orderId, courier.id());
        }
    }

    private CourierData findNearestIdleCourier(Order order) {

        //Get the restaurant's location
        Restaurant restaurant = restaurantService.load(order.getRestaurantId());
        Location location = restaurant.getLocation();

        if (location == null) {
            throw new IllegalStateException("Restaurant has no location");
        }

        CourierData nearestCourier = null;
        double minDistance = Double.POSITIVE_INFINITY;

        //Loop through all online couriers
        for (CourierData courier : courierService.getIdleCouriers()) {

            // Calculate squared Euclidean distance
            double latDiff = location.latitude() - courier.latitude();
            double lonDiff = location.longitude() - courier.longitude();

            double distance = latDiff * latDiff + lonDiff * lonDiff;

            //Check if closer, or tied with a smaller courier ID
            if (nearestCourier == null || 
            Double.compare(distance, minDistance) < 0 || 
            (Double.compare(distance, minDistance) == 0 && courier.id().compareTo(nearestCourier.id()) < 0)) {
                nearestCourier = courier;
                minDistance = distance;
            }
        }

        return nearestCourier;
    }

    @Transactional
    public void offerOrder(UUID orderId, UUID courierId) {
        Order order = orderService.load(orderId);
        order.assignCourier(courierId);
        orderService.save(order);

        eventPublisher.publishEvent(new OrderOfferedEvent(orderId, courierId));
    }

    public Order getPendingOffer(UUID courierId) {
        for (UUID orderId : queues.getGlobalReady()) {
            Order order = orderService.load(orderId);

            if (courierId.equals(order.getCourierId())) {
                return order;
            }
        }
        return null;
    }
}
