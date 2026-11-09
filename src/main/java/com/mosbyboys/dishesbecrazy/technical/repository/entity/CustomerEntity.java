package com.mosbyboys.dishesbecrazy.technical.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "\"Customer\"")
@Getter @Setter
@NoArgsConstructor
public class CustomerEntity {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    private String address;

    private Double latitude;
    private Double longitude;
}
