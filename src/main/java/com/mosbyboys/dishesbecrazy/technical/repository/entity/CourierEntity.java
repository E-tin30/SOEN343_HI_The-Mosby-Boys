package com.mosbyboys.dishesbecrazy.technical.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;

@Entity
@Table(name = "\"Courier\"")
@Getter
@Setter
@NoArgsConstructor
public class CourierEntity {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private CourierState state;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;
}

