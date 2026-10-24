package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;

import lombok.RequiredArgsConstructor;

/* The queues only live in memory, so on startup put every order that is still waiting back in
its line, oldest first. Without this, orders placed before a restart can never be first in line
and can't be accepted or prepared. Orders are accepted in the order they were placed, so placing
time is also the preparation order. */
@Component
@RequiredArgsConstructor
public class OrderQueueLoader implements CommandLineRunner {

    private final OrderService orderService;
    private final OrderQueues queues;

    @Override
    public void run(String... args) {
        List<Order> orders = orderService.findInStates(List.of("PlacedState", "AcceptedState", "ReadyState"));
        for (Order order : orders) {
            switch (order.getState().toString()) {
                case "PlacedState" -> queues.placed(order.getRestaurantId()).enqueue(order.getId());
                case "AcceptedState" -> queues.preparation(order.getRestaurantId()).enqueue(order.getId());
                case "ReadyState" -> queues.ready(order.getRestaurantId()).enqueue(order.getId());
                default -> { }
            }
        }
    }
}
