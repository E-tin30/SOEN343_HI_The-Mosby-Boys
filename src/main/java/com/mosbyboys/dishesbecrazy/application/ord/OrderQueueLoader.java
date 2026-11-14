package com.mosbyboys.dishesbecrazy.application.ord;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;

import lombok.RequiredArgsConstructor;

/* Rebuilds the in-memory queues from persisted waiting orders, oldest first.
Startup and future demo restoration share the same state routing and FIFO rules. */
@Component
@RequiredArgsConstructor
public class OrderQueueLoader implements CommandLineRunner {

    private final OrderService orderService;
    private final OrderQueues queues;

    @Override
    public void run(String... args) {
        rebuild();
    }
    // Startup uses the same replace operation that restoration can invoke later.

    public void rebuild() {
        List<Order> orders = orderService.findInStates(List.of("PlacedState", "AcceptedState", "ReadyState"));
        queues.clear();
        // Finish reading persistence before clearing memory so a failed query preserves the current queues.

        for (Order order : orders) {
            switch (order.getState().toString()) {
                case "PlacedState" -> queues.placed(order.getRestaurantId()).enqueue(order.getId());
                case "AcceptedState" -> queues.preparation(order.getRestaurantId()).enqueue(order.getId());
                case "ReadyState" -> queues.ready(order.getRestaurantId()).enqueue(order.getId());
                default -> { }
            }
        }
    }
    // Call only at startup or while the reset coordinator has paused order/dispatch changes.
}
