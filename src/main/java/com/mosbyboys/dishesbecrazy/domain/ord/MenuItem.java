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
    private Instant createdAt; 

    private String name;
    private BigDecimal price;
    private int stockQuantity;

    @Enumerated(EnumType.STRING)         
    private MenuItemStatus status; 

    private String imageUrl;
    private String description;
}
