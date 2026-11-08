package com.mosbyboys.dishesbecrazy.ui.controller;

import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuControllerTest {
    @ParameterizedTest
    @CsvSource({"AVAILABLE, 0, SOLD_OUT", "AVAILABLE, 3, AVAILABLE", "SOLD_OUT, 3, SOLD_OUT"})
    void exposesOrderableAvailabilityRatherThanJustStoredStatus(MenuItemStatus status, int stock, String expected) {
        MenuItem item = new MenuItem(UUID.randomUUID(), UUID.randomUUID(), "Soup", new BigDecimal("10.00"),
                stock, status, null, null, Instant.now());

        assertEquals(expected, MenuController.MenuItemResponse.from(item).status());
    }
}
