package com.mosbyboys.dishesbecrazy.application.ntf;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderObserver;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* Concrete subscriber (R-NTF-01): tells the customer every time their order changes state.
Runs inside the order's transaction, so it must never throw: a failed notification must not undo the order change. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotifier implements OrderObserver {

    // %1$s is the restaurant name, %2$s the order number.
    static final Map<String, String> MESSAGES = Map.of(
        "PlacedState", "Your order %2$s at %1$s was placed.",
        "AcceptedState", "%1$s accepted your order %2$s.",
        "RejectedState", "%1$s rejected your order %2$s.",
        "PreparingState", "%1$s is preparing your order %2$s.",
        "ReadyState", "Your order %2$s at %1$s is ready and waiting for a courier.",
        "PickedUpState", "Your order %2$s from %1$s is on the way.",
        "DeliveredState", "Your order %2$s from %1$s was delivered.",
        "CancelledState", "Your order %2$s at %1$s was cancelled.",
        "RefundedState", "Your order %2$s from %1$s was refunded."
    );

    private final ORDFacade ordFacade;
    private final NotificationService notifications;

    @Override
    public void update(OrderStateChanged event) {
        try {
            String template = MESSAGES.get(event.newState());
            if (template == null) {
                return;
            }
            Order order = ordFacade.getOrder(event.orderId());
            String restaurant = ordFacade.getRestaurantNames(Set.of(order.getRestaurantId()))
                    .getOrDefault(order.getRestaurantId(), "the restaurant");

            notifications.notify(order.getCustomerId(), event.newState(), template.formatted(restaurant, OrderNumber.of(order.getId())), order.getId());
        } catch (RuntimeException e) {
            log.warn("Could not notify the customer about order {}", event.orderId(), e);
        }
    }
}