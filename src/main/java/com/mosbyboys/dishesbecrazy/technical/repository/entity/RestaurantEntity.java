package com.mosbyboys.dishesbecrazy.technical.repository.entity;

import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "\"Restaurant\"")
@Getter @Setter
@NoArgsConstructor
public class RestaurantEntity {

    // The restaurant's id is its owner's User.id.
    @Id
    @Column(name = "user_id")
    private UUID id;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantStatus status;

    private Double latitude;
    private Double longitude;
}
