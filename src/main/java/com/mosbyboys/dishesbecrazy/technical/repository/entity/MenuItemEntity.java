package com.mosbyboys.dishesbecrazy.technical.repository.entity;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/* Database row for public."MenuItem". MenuService turns it into the MenuItem domain object. */
@Entity
@Table(name = "\"MenuItem\"")
@Getter @Setter
@NoArgsConstructor
public class MenuItemEntity {

    @Id
    private UUID id;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    private String name;
    private BigDecimal price;

    @Column(name = "stock_quantity")
    private int stockQuantity;

    @Enumerated(EnumType.STRING)
    private MenuItemStatus status;

    @Column(name = "image_url")
    private String imageUrl;

    private String description;
}
