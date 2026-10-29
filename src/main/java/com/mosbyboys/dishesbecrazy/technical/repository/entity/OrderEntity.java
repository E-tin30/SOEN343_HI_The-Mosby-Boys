package com.mosbyboys.dishesbecrazy.technical.repository.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "\"Order\"")
@Getter @Setter
@NoArgsConstructor
public class OrderEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "prepared_at")
    private Instant preparedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "total")
    private BigDecimal total;
}