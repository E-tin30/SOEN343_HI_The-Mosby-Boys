package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.domain.reg.User;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
public class MenuController {

    private final ORDFacade ordFacade;

    @GetMapping("/api/restaurants/{restaurantId}/menu")
    public ResponseEntity<List<MenuItem>> getMenu(User user, @PathVariable UUID restaurantId) {
        List<MenuItem> menuItems = ordFacade.getMenuForRestaurant(user, restaurantId);
        return ResponseEntity.ok(menuItems);
    }
}