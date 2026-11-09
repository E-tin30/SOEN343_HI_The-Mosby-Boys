package com.mosbyboys.dishesbecrazy.application.dlv;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;

import lombok.RequiredArgsConstructor;

/* The global ready queue only lives in memory. An order in it is waiting for a courier or offered to one;
an accepted order has left it. On startup, put back every READY order that no courier has accepted yet,
otherwise offers turn into "accepted" deliveries the courier never agreed to, and unassigned orders are
never dispatched */
@Component
@RequiredArgsConstructor
public class DispatchQueueLoader implements CommandLineRunner {

    private final OrderService orderService;
    private final OrderQueues queues;
    private final CourierService courierService;
    private final DispatchService dispatchService;

    @Override
    public void run(String... args) {
        // State of every courier who is on duty; anyone missing is OFFLINE
        Map<UUID, String> onlineStates = courierService.getOnlineCouriers().stream()
            .collect(Collectors.toMap(CourierData::id, CourierData::state));

        List<Order> readyOrders = orderService.findInStates(List.of("ReadyState"));
        for (Order order : readyOrders) {
            if (order.getCourierId() != null) {
                String state = onlineStates.getOrDefault(order.getCourierId(), "OFFLINE");
                // Accepted: the courier is heading to the restaurant, so it is no longer waiting
                if ("ASSIGNED".equalsIgnoreCase(state) || "CARRYING".equalsIgnoreCase(state)) {
                    continue;
                }
                // The courier went offline with the offer still open: take it back
                if (!"IDLE".equalsIgnoreCase(state)) {
                    order.clearCourierOffer(order.getCourierId());
                    orderService.save(order);
                }
            }
            queues.enqueueGlobalReady(order.getId());
        }
        dispatchService.dispatchWaitingOrders();
    }
}
