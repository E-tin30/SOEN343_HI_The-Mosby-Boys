package com.mosbyboys.dishesbecrazy.domain.ord;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/* One menu item plus a quantity inside an order. Quantity must be above 0. */
@Getter
public class OrderLine {

    private final UUID menuItemId;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final String menuItemName;

    public OrderLine(UUID menuItemId, int quantity, BigDecimal unitPrice, String menuItemName) {
        
        // unit price not null
        if(unitPrice == null){
            throw new IllegalArgumentException("unitPrice must not be null");
        }

        // menu item id not null
        if(menuItemId == null){
            throw new IllegalArgumentException("menuItemId must not be null");
        }

        // quantity > 0
        if(quantity <= 0){
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        // unitPrice > 0
        if(unitPrice.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Unit price must be greater than 0");
        }

        this.menuItemId = menuItemId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.menuItemName = menuItemName;
    }

    // get the total price of the line item (unitPrice x quantity)
    public BigDecimal getLineTotal(){
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

}
