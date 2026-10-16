package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.technical.repository.entity.OrderLineItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderLineItemRepo extends JpaRepository<OrderLineItemEntity, UUID> {
    List<OrderLineItemEntity> findByOrderId(UUID orderId);
}
