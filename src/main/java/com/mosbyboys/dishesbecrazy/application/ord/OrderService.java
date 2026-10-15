package com.mosbyboys.dishesbecrazy.application.ord;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;
import com.mosbyboys.dishesbecrazy.technical.repository.OrderLineItemRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.OrderRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.OrderEntity;
import com.mosbyboys.dishesbecrazy.technical.repository.entity.OrderLineItemEntity;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


/*
ORD services that are required by ORD but not publicly visible through ORDFacade.
*/
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepo orderRepo;
    private final OrderLineItemRepo lineItemRepo;

    @Transactional
    public void save(Order order) {
        Optional<OrderEntity> existing = orderRepo.findById(order.getId());
        boolean isNew = existing.isEmpty();

        OrderEntity orderEntity = existing.orElseGet(OrderEntity::new);
        orderEntity.setId(order.getId());
        orderEntity.setUserId(order.getUserId());
        orderEntity.setRestaurantId(order.getRestaurantId());
        orderEntity.setState(order.getState().toString());
        orderEntity.setCreatedAt(order.getPlacedAt());
        orderEntity.setRejectionReason(order.getRejectionReason());

        orderRepo.saveAndFlush(orderEntity);

        if (isNew) {
            List<OrderLineItemEntity> lines = order.getOrderLineItems().stream().map(line -> {
                OrderLineItemEntity e = new OrderLineItemEntity();
                e.setOrderId(order.getId());
                e.setMenuItemId(line.getMenuItemId());
                e.setQuantity(line.getQuantity());
                e.setUnitPrice(line.getUnitPrice());
                return e;
            }).toList();
            lineItemRepo.saveAll(lines);
        }
    }

    @Transactional(readOnly = true)
    public Order load(UUID orderId) {
        OrderEntity entity = orderRepo.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        List<OrderLine> lines = lineItemRepo.findByOrderId(orderId).stream().map(e -> new OrderLine(e.getMenuItemId(), e.getQuantity(), e.getUnitPrice())).toList();

        return Order.reconstitute(
            entity.getId(),
            OrderStates.fromName(entity.getState()),
            entity.getUserId(),
            entity.getRestaurantId(),
            lines,
            entity.getCreatedAt(),
            entity.getRejectionReason()
        );
    }
}
