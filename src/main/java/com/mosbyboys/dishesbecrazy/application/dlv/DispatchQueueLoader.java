package com.mosbyboys.dishesbecrazy.application.dlv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.mosbyboys.dishesbecrazy.application.ord.OrderQueues;
import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;

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
        rebuild();
        dispatchService.dispatchWaitingOrders();
    }

    // Replaces the global ready queue with the READY orders no courier has accepted, oldest first.
    public void rebuild() {
        // State of every courier who is on duty; anyone missing is OFFLINE
        Map<UUID, CourierState> onlineStates = courierService.getOnlineCouriers().stream()
            .collect(Collectors.toMap(CourierData::id, CourierData::state));

        List<UUID> waiting = new ArrayList<>();
        List<Order> readyOrders = orderService.findInStates(List.of("ReadyState"));
        for (Order order : readyOrders) {
            if (order.getCourierId() != null) {
                CourierState state = onlineStates.getOrDefault(order.getCourierId(), CourierState.OFFLINE);
                if (state == CourierState.ASSIGNED || state == CourierState.CARRYING) {
                    continue;
                }
                if (state != CourierState.IDLE) {
                    order.clearCourierOffer(order.getCourierId());
                    orderService.save(order);
                }
            }
            waiting.add(order.getId());
        }
        // Swap only after every read succeeded, so a failed read leaves the current queue intact
        queues.replaceGlobalReady(waiting);
    }
}
