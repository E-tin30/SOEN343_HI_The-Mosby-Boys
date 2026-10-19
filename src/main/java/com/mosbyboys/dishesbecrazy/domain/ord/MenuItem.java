package com.mosbyboys.dishesbecrazy.domain.ord;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

/* One dish on a restaurant's menu. Stock never goes below 0, and the item is SOLD_OUT when stock hits 0.
Plain domain class: MenuItemEntity is the database row, MenuService converts between them. */
@Getter
public class MenuItem {

    private final UUID id;
    private final UUID restaurantId;
    private final String name;
    private final BigDecimal price;
    private final String description;
    private final String imageUrl;
    private final Instant createdAt;
    private int stockQuantity;
    private MenuItemStatus status;

    public MenuItem(UUID id, UUID restaurantId, String name, BigDecimal price, int stockQuantity,
                    MenuItemStatus status, String description, String imageUrl, Instant createdAt) {
        if (id == null) {
            throw new IllegalArgumentException("Menu item id must not be null");
        }
        if (restaurantId == null) {
            throw new IllegalArgumentException("Menu item must belong to a restaurant");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Menu item name must not be blank");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Menu item price must be greater than 0");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity must not be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("Menu item status must not be null");
        }
        this.id = id;
        this.restaurantId = restaurantId;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.status = status;
        this.description = description;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
    }

    // R-ORD-02: only available items with stock can be ordered
    public boolean isAvailable() {
        return status == MenuItemStatus.AVAILABLE && stockQuantity > 0;
    }

    public boolean belongsTo(UUID restaurantId) {
        return this.restaurantId.equals(restaurantId);
    }

    // UC-ORD-05: the owner flips availability by hand
    public void markSoldOut() {
        this.status = MenuItemStatus.SOLD_OUT;
    }

    public void markAvailable() {
        if (stockQuantity == 0) {
            throw new IllegalStateException("Cannot mark " + name + " available with no stock");
        }
        this.status = MenuItemStatus.AVAILABLE;
    }

    // Takes stock for an order. Never below 0; the item sells out at 0.
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
        if (quantity > stockQuantity) {
            throw new IllegalStateException("Only " + stockQuantity + " of " + name + " left");
        }
        this.stockQuantity -= quantity;
        if (this.stockQuantity == 0) {
            this.status = MenuItemStatus.SOLD_OUT;
        }
    }
}
