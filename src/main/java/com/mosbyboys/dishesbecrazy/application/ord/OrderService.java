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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


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
        orderEntity.setUserId(order.getCustomerId());
        orderEntity.setRestaurantId(order.getRestaurantId());
        orderEntity.setCourierId(order.getCourierId());
        orderEntity.setState(order.getState().toString());
        orderEntity.setCreatedAt(order.getPlacedAt());
        orderEntity.setRejectionReason(order.getRejectionReason());
        orderEntity.setSubtotal(order.getSubtotal());

        orderRepo.saveAndFlush(orderEntity);

        if (isNew) {
            List<OrderLineItemEntity> lines = order.getOrderLineItems().stream().map(line -> {
                OrderLineItemEntity e = new OrderLineItemEntity();
                e.setOrderId(order.getId());
                e.setMenuItemId(line.getMenuItemId());
                e.setMenuItemName(line.getMenuItemName());
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

        List<OrderLine> lines = lineItemRepo.findByOrderId(orderId).stream().map(OrderService::toLine).toList();

        return toDomain(entity, lines);
    }

    // A restaurant's orders in the given states, oldest first. Lines for all of them come from one query.
    @Transactional(readOnly = true)
    public List<Order> findForRestaurant(UUID restaurantId, Collection<String> states) {
        return withLines(orderRepo.findByRestaurantIdAndStateInOrderByCreatedAtAsc(restaurantId, states));
    }

    // Every restaurant's orders in the given states, oldest first. Used to rebuild the queues on startup.
    @Transactional(readOnly = true)
    public List<Order> findInStates(Collection<String> states) {
        return withLines(orderRepo.findByStateInOrderByCreatedAtAsc(states));
    }

    // Loads the lines for all the orders in one query and builds the domain orders, keeping their order.
    private List<Order> withLines(List<OrderEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<OrderLine>> linesByOrder = lineItemRepo
                .findByOrderIdIn(entities.stream().map(OrderEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderLineItemEntity::getOrderId,
                        Collectors.mapping(OrderService::toLine, Collectors.toList())));

        return entities.stream()
                .map(e -> toDomain(e, linesByOrder.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    private static OrderLine toLine(OrderLineItemEntity e) {
        return new OrderLine(e.getMenuItemId(), e.getQuantity(), e.getUnitPrice(), e.getMenuItemName());
    }

    private static Order toDomain(OrderEntity entity, List<OrderLine> lines) {
        return Order.reconstitute(
            entity.getId(),
            OrderStates.fromName(entity.getState()),
            entity.getUserId(),
            entity.getRestaurantId(),
            entity.getCourierId(),
            lines,
            entity.getCreatedAt(),
            entity.getRejectionReason(),
            entity.getSubtotal()
        );
    }

    // States in which a courier has actually carried the order (an offered-only order doesn't count).
    private static final List<String> CARRIED_STATES =
            List.of("PickedUpState", "DeliveredState", "RefundedState");

    // A customer's own orders, newest first.
    @Transactional(readOnly = true)
    public List<Order> findHistoryForCustomer(UUID customerId) {
        return withLines(orderRepo.findByUserIdOrderByCreatedAtDesc(customerId));
    }

    // A restaurant's own orders, newest first.
    @Transactional(readOnly = true)
    public List<Order> findHistoryForRestaurant(UUID restaurantId) {
        return withLines(orderRepo.findByRestaurantIdOrderByCreatedAtDesc(restaurantId));
    }

    // Only the deliveries this courier carried, newest first.
    @Transactional(readOnly = true)
    public List<Order> findHistoryForCourier(UUID courierId) {
        return withLines(orderRepo.findByCourierIdAndStateInOrderByCreatedAtDesc(courierId, CARRIED_STATES));
    }

    // Every order, newest first (operator).
    @Transactional(readOnly = true)
    public List<Order> findHistoryForAll() {
        return withLines(orderRepo.findAllByOrderByCreatedAtDesc());
    }
}