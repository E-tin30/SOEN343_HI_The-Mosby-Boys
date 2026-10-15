package com.mosbyboys.dishesbecrazy.technical.repository;

import com.mosbyboys.dishesbecrazy.technical.repository.entity.OrderEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/* Saves and loads order events. Only ever adds, never edits. */
public interface OrderEventRepo extends JpaRepository<OrderEventEntity, UUID> {}
