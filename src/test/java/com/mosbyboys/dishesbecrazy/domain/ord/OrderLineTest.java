package com.mosbyboys.dishesbecrazy.domain.ord;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderLineTest {

    // a valid order line keeps its menu item id, quantity and unit price
    @Test
    void validLineKeepsItsValues() {
        UUID menuItemId = UUID.randomUUID();
        BigDecimal unitPrice = new BigDecimal("12.50");
        OrderLine line = new OrderLine(menuItemId, 3, unitPrice, null);

        assertEquals(menuItemId, line.getMenuItemId());
        assertEquals(3, line.getQuantity());
        assertEquals(0, unitPrice.compareTo(line.getUnitPrice()));
    }

    // zero quantity throws exception
    @Test
    void quantityZeroThrows() {
        UUID menuItemId = UUID.randomUUID();
        BigDecimal unitPrice = new BigDecimal("12.50");
        assertThrows(IllegalArgumentException.class, () -> new OrderLine(menuItemId, 0, unitPrice, null));
    }

    // zero price throws exception
    @Test
    void priceZeroThrows() {
        UUID menuItemId = UUID.randomUUID();
        BigDecimal unitPrice = new BigDecimal("0");
        assertThrows(IllegalArgumentException.class, () -> new OrderLine(menuItemId, 3, unitPrice, null));
    }

    // negative quantity throws exception
    @Test
    void negativeQuantityThrows() {
        UUID menuItemId = UUID.randomUUID();
        BigDecimal unitPrice = new BigDecimal("12.50");
        assertThrows(IllegalArgumentException.class, () -> new OrderLine(menuItemId, -2, unitPrice, null));
    }

    // negative price throws exception
    @Test
    void negativePriceThrows() {
        UUID menuItemId = UUID.randomUUID();
        BigDecimal unitPrice = new BigDecimal("-12.50");
        assertThrows(IllegalArgumentException.class, () -> new OrderLine(menuItemId, 3, unitPrice, null));
    }

    // null price throws exception
    @Test
    void nullPriceThrows() {
        UUID menuItemId = UUID.randomUUID();
        BigDecimal unitPrice = null;
        assertThrows(IllegalArgumentException.class, () -> new OrderLine(menuItemId, 3, unitPrice, null));
    }

    // null menuItemId throws exception
    @Test
    void menuItemIdThrows() {
        UUID menuItemId = null;
        BigDecimal unitPrice = new BigDecimal("12.50");
        assertThrows(IllegalArgumentException.class, () -> new OrderLine(menuItemId, 3, unitPrice, null));
    }

    // getLineTotal() returns correct value
    @Test
    void getLineTotalReturnsCorrectValue() {
        UUID menuItemId = UUID.randomUUID();
        BigDecimal unitPrice = new BigDecimal("12.50");
        OrderLine line = new OrderLine(menuItemId, 3, unitPrice, null);
        BigDecimal expectedLineTotal = new BigDecimal("37.50");

        assertEquals(0, line.getLineTotal().compareTo(expectedLineTotal));
    }
}