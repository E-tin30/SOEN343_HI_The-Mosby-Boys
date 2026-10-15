package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/* Runs the ORD use cases (place, accept, reject, prepare, ready, cancel, get).
Every command: check permission, change the order's state, save it, record an event.
No per-request data in fields, Spring only makes one of these. (Singleton) */
@Service
@RequiredArgsConstructor
public class ORDFacade {

    private final OrderService orderService;
    private final OrderEventService orderEventService;

    @Transactional
    public Order placeOrder(UUID userId, UUID restaurantId, List<OrderLine> orderLineItems) {
        //TODO: check with permission policy class before proceeding - must be customer

        Order order = new Order(userId, restaurantId, orderLineItems);

        //save order to db
        orderService.save(order);

        return order;
    }

    @Transactional
    public void acceptOrder(UUID userId, UUID orderId) {
        //TODO: check with permission policy class before proceeding - must be restaurant

        Order order = orderService.load(orderId);
        String before = order.getState().toString();

        order.accept();

        //save state change event
        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), userId, "ACCEPT");
    }

    @Transactional
    public void rejectOrder(UUID userId, UUID orderId, String reason) {
        //Todo: check with permissions first - must be restaurant

        Order order = orderService.load(orderId);
        String before = order.getState().toString();

        order.reject(reason);

        //save to db
        orderService.save(order);
        orderEventService.save(orderId, before, order.getState().toString(), userId, "REJECT");
    }

}
