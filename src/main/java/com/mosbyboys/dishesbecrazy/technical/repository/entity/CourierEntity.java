package com.mosbyboys.dishesbecrazy.technical.repository.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "\"Courier\"")
@Getter
@Setter
@NoArgsConstructor
public class CourierEntity {

    @Id
    @Column(name = "user_id")
    private UUID id;

    @Column(name = "state")
    private String state;

    private Double latitude;
    private Double longitude;
}

