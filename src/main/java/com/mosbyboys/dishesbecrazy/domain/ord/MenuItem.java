package com.mosbyboys.dishesbecrazy.domain.ord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "\"MenuItem\"")
public class MenuItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) 
    private UUID id;

    @Column(insertable = false, updatable = false)
    private Instant createdAt; // confirm we want to use instant

    private String name;
    private BigDecimal price;
    private int stockQuantity;

    @Enumerated(EnumType.STRING)          // stores "AVAILABLE" / "SOLD_OUT" as text
    private MenuItemStatus status; // should this be derived? enum?

    private String imageUrl;
    private String description;
}
