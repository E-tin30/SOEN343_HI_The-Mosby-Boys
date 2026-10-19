package com.mosbyboys.dishesbecrazy.technical.repository.entity;

import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/* Database row for public."Restaurant". RestaurantService turns it into the Restaurant domain object. */
@Entity
@Table(name = "\"Restaurant\"")
@Getter @Setter
@NoArgsConstructor
public class RestaurantEntity {

    @Id
    private UUID id;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantStatus status;

    private Double latitude;
    private Double longitude;

    @Column(name = "user_id")
    private UUID ownerId;
}
