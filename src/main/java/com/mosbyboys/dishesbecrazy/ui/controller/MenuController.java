package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.oauth2.jwt.Jwt;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import com.mosbyboys.dishesbecrazy.domain.reg.User;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
public class MenuController {

    private final ORDFacade ordFacade;
    private final REGFacade regFacade;

    public record MenuItemResponse(UUID id, String name, BigDecimal price, String status, String description) {
    static MenuItemResponse from(MenuItem m) {
        String availability = (m.isAvailable() ? MenuItemStatus.AVAILABLE : MenuItemStatus.SOLD_OUT).name();
        // Exposes ORD's existing availability rule, including zero stock, in the menu status field.
        return new MenuItemResponse(m.getId(), m.getName(), m.getPrice(), availability, m.getDescription());
    }
}

    @GetMapping("/api/restaurants/{restaurantId}/menu")
    public ResponseEntity<List<MenuItemResponse>> getMenu(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID restaurantId) {
        User user = regFacade.getCurrentUser(UUID.fromString(jwt.getSubject()));
        List<MenuItem> body = ordFacade.getMenuForRestaurant(user, restaurantId);
        List<MenuItemResponse> menuItems = body.stream()
            .map(MenuItemResponse::from)
            .toList();
        return ResponseEntity.ok(menuItems);
    }
}
