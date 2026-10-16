package com.mosbyboys.dishesbecrazy.technical.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table (name = "\"Event\"")
@Getter @Setter
@NoArgsConstructor
public class OrderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "previous_state")
    private String previousState;

    @Column(name = "new_state", nullable = false)
    private String newState;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "performed_by")
    private UUID performedBy;

    private String action;
}
