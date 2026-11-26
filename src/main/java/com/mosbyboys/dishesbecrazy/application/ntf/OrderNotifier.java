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

    // %s is the restaurant name.
    static final Map<String, String> MESSAGES = Map.of(
            "PlacedState", "Your order at %s was placed.",
            "AcceptedState", "%s accepted your order.",
            "RejectedState", "%s rejected your order.",
            "PreparingState", "%s is preparing your order.",
            "ReadyState", "Your order at %s is ready and waiting for a courier.",
            "PickedUpState", "Your order from %s is on the way.",
            "DeliveredState", "Your order from %s was delivered.",
            "CancelledState", "Your order at %s was cancelled.",
            "RefundedState", "Your order from %s was refunded.");

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

            notifications.notify(order.getCustomerId(), event.newState(),
                    template.formatted(restaurant), order.getId());
        } catch (RuntimeException e) {
            log.warn("Could not notify the customer about order {}", event.orderId(), e);
        }
    }
}