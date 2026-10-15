package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.technical.repository.OrderEventRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.OrderEventEntity;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderEventService {

    private final OrderEventRepo orderEventRepo;

    @Transactional
    public OrderEventEntity save(UUID orderId, String previousState, String newState, UUID performedBy, String action) {
        OrderEventEntity orderEvent = new OrderEventEntity();

        orderEvent.setOrderId(orderId);
        orderEvent.setPreviousState(previousState);
        orderEvent.setNewState(newState);
        orderEvent.setPerformedBy(performedBy);
        orderEvent.setAction(action);

        return orderEventRepo.save(orderEvent);
    }
}
