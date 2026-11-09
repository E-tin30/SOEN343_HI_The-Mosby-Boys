package com.mosbyboys.dishesbecrazy.technical.repository.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "\"Checkout\"")
@Getter @Setter @NoArgsConstructor
public class CheckoutEntity implements Persistable<UUID> {
    @Id
    private UUID id;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;
    @Column(name = "lines_json", nullable = false, columnDefinition = "text")
    private String linesJson;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "total_minor", nullable = false)
    private long totalMinor;
    @Column(name = "payment_intent_id", unique = true)
    private String paymentIntentId;
    @Column(nullable = false)
    private boolean completed;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Transient
    private boolean newRecord = true;

    @Override
    public boolean isNew() {
        return newRecord;
    }

    @PostLoad @PostPersist
    void markPersisted() {
        newRecord = false;
    }
    // Assigned checkout IDs must INSERT on first save; a concurrent retry must not merge over another snapshot.
}
