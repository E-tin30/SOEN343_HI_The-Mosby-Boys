package com.mosbyboys.dishesbecrazy.application.dlv;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.state.ReadyState;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;
import com.mosbyboys.dishesbecrazy.application.ord.RestaurantService;


import java.util.UUID;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final PlatformTransactionManager transactionManager;

    /* Only one dispatch runs at a time, otherwise two runs could both see the same order without 
    a courier and offer it twice.*/
    public synchronized void dispatchWaitingOrders() {
        inNewTransaction(this::offerWaitingOrders);
    }

    /* Takes the courier OFFLINE, giving back any offer they hold. It shares the dispatch lock, so a
    dispatch can't offer this courier an order while they go offline. */
    public synchronized void goOffline(UUID courierId) {
        boolean gaveBackOffer = inNewTransaction(() -> {
            Order offer = getPendingOffer(courierId);
            if (offer != null) {
                offer.clearCourierOffer(courierId);
                orderService.save(offer);
            }
            courierService.markOffline(courierId);
            return offer != null;
        });

        // Offer the given-back order to another courier. Same thread, so the lock can be re-entered
        if (gaveBackOffer) {
            dispatchWaitingOrders();
        }
    }

    // Runs the work in its own transaction and ensures changes are committed before returning
    private <T> T inNewTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> work.get());
    }

    private void inNewTransaction(Runnable work) {
        inNewTransaction(() -> {
            work.run();
            return null;
        });
    }

    private void offerWaitingOrders() {
        for (UUID orderId : queues.globalReadySnapshot()) {

            Order order = orderService.load(orderId);

            // Drop orders that are no longer READY (e.g. cancelled) so they can't block the rest of the queue
            if (!(order.getState() instanceof ReadyState)) {
                queues.removeGlobalReady(orderId);
                continue;
            }

            // Skip orders already offered or assigned
            if (order.getCourierId() != null) {
                continue;
            }

            // Skip orders whose restaurant has no location (e.g. deleted)
            Location pickup = restaurantService.load(order.getRestaurantId()).getLocation();
            if (pickup == null) {
                continue;
            }

            // Find the nearest available courier
            CourierData courier = findNearestIdleCourier(pickup);

            // No eligible courier available
            if (courier == null) {
                break;
            }

            // Offer order to the selected courier
            offerOrder(orderId, courier.id());
        }
    }

    private CourierData findNearestIdleCourier(Location location) {

        CourierData nearestCourier = null;
        double minDistance = Double.POSITIVE_INFINITY;

        //Loop through all online couriers
        for (CourierData courier : courierService.getIdleCouriers()) {
            //Check if the courier already has an offer
             if (getPendingOffer(courier.id()) != null) {
                continue;
            }

            //Calculate squared Euclidean distance
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
    }

    /* An offer is an order given to this courier that is still in the ready queue (accepting removes it).
    Loads only this courier's current orders in one query instead of loading every queued order. */
    public Order getPendingOffer(UUID courierId) {
        Set<UUID> queued = new HashSet<>(queues.globalReadySnapshot());
        for (Order order : orderService.findCurrentForCourier(courierId)) {
            if (queued.contains(order.getId())) {
                return order;
            }
        }
        return null;
    }
    
    @Transactional
    public void acceptOffer(UUID courierId, UUID orderId) {
        //Verify the courier actually has this pending offer
        Order offer = getPendingOffer(courierId);
        if (offer == null || !offer.getId().equals(orderId)) {
            throw new IllegalStateException(
                "Courier does not have this pending offer"
            );
        }

        //Change courier from IDLE to ASSIGNED
        courierService.markAssigned(courierId);

        //Remove the accepted order, not necessarily the head
        boolean removed = queues.removeGlobalReady(orderId);
        if (!removed) {
            throw new IllegalStateException("Order is not queued");
        }
    }

    public synchronized void reassignCourier(
        UUID oldCourierId, UUID newCourierId) {

        Order order = inNewTransaction(() -> {

            //Verify that the old courier is assigned
            if (courierService.getState(oldCourierId) != CourierState.ASSIGNED) {
                throw new IllegalStateException(
                    "Old courier must be ASSIGNED"
                );
            }

            //Verify that the new courier is idle
            if (courierService.getState(newCourierId)!= CourierState.IDLE) {
                throw new IllegalStateException(
                    "New courier must be IDLE"
                );
            }

            //Find the order associated with the old courier
            Order currentOrder = orderService
                .findCurrentForCourier(oldCourierId)
                .stream()
                .filter(o -> o.getState() instanceof ReadyState)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No ready order found for the old courier"
                ));

            //Replace the courier ID and save the order
            currentOrder.assignCourier(newCourierId);
            orderService.save(currentOrder);

            //Release the old courier
            courierService.release(oldCourierId);

            return currentOrder;
        });

        //Add the order back to the global ready queue
        if (!queues.globalReadySnapshot().contains(order.getId())) {
            queues.enqueueGlobalReady(order.getId());
        }

        //Ensure the order is in the restaurant's ready queue
        if (!queues.ready(order.getRestaurantId()).contains(order.getId())) {
            queues.ready(order.getRestaurantId()).enqueue(order.getId());
        }
    }


}
