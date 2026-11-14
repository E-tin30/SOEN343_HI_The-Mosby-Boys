package com.mosbyboys.dishesbecrazy.domain.ord;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MenuItemTest {

    private final UUID restaurantId = UUID.randomUUID();

    private MenuItem item(int stock, MenuItemStatus status) {
        return new MenuItem(UUID.randomUUID(), restaurantId, "Lasagna", new BigDecimal("16.50"), stock,
                status, "Seven layers", null, Instant.now());
    }

    // an available item with stock can be ordered
    @Test
    void availableWithStockIsAvailable() {
        assertTrue(item(5, MenuItemStatus.AVAILABLE).isAvailable());
    }

    // AVAILABLE but no stock is not orderable
    @Test
    void availableWithNoStockIsNotAvailable() {
        assertFalse(item(0, MenuItemStatus.AVAILABLE).isAvailable());
    }

    // decreasing stock to 0 sells the item out
    @Test
    void decreaseStockToZeroSellsOut() {
        MenuItem m = item(3, MenuItemStatus.AVAILABLE);
        m.decreaseStock(3);
        assertEquals(0, m.getStockQuantity());
        assertEquals(MenuItemStatus.SOLD_OUT, m.getStatus());
    }

    // taking more than is in stock throws, and stock is unchanged
    @Test
    void decreaseStockPastZeroThrows() {
        MenuItem m = item(2, MenuItemStatus.AVAILABLE);
        assertThrows(IllegalStateException.class, () -> m.decreaseStock(3));
        assertEquals(2, m.getStockQuantity());
    }

    // zero or negative quantity throws
    @Test
    void decreaseStockByZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> item(2, MenuItemStatus.AVAILABLE).decreaseStock(0));
    }

    // owner can flip availability
    @Test
    void markSoldOutThenAvailable() {
        MenuItem m = item(4, MenuItemStatus.AVAILABLE);
        m.markSoldOut();
        assertFalse(m.isAvailable());
        m.markAvailable();
        assertTrue(m.isAvailable());
    }

    // cannot mark available with no stock
    @Test
    void markAvailableWithNoStockThrows() {
        assertThrows(IllegalStateException.class, () -> item(0, MenuItemStatus.SOLD_OUT).markAvailable());
    }

    // belongsTo only matches its own restaurant
    @Test
    void belongsToOwnRestaurantOnly() {
        MenuItem m = item(1, MenuItemStatus.AVAILABLE);
        assertTrue(m.belongsTo(restaurantId));
        assertFalse(m.belongsTo(UUID.randomUUID()));
    }

    // zero price throws exception
    @Test
    void zeroPriceThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MenuItem(UUID.randomUUID(), restaurantId, "X",
                BigDecimal.ZERO, 1, MenuItemStatus.AVAILABLE, null, null, null));
    }

    // negative stock throws exception
    @Test
    void negativeStockThrows() {
        assertThrows(IllegalArgumentException.class, () -> item(-1, MenuItemStatus.AVAILABLE));
    }

    // missing restaurant throws exception
    @Test
    void nullRestaurantThrows() {
        assertThrows(IllegalArgumentException.class, () -> new MenuItem(UUID.randomUUID(), null, "X",
                BigDecimal.ONE, 1, MenuItemStatus.AVAILABLE, null, null, null));
    }

    // restoring stock must not override a restaurant's manual SOLD_OUT
    @Test
    void increaseStockKeepsAManualSoldOut() {
        MenuItem m = item(2, MenuItemStatus.SOLD_OUT);
        m.increaseStock(1);
        assertEquals(3, m.getStockQuantity());
        assertEquals(MenuItemStatus.SOLD_OUT, m.getStatus());
    }
}
