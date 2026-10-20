package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.reg.User;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
public class RestaurantController {

    private final ORDFacade ordFacade;

    @GetMapping("/api/restaurants")
    public ResponseEntity<List<Restaurant>> getRestaurants(User user) {
        List<Restaurant> restaurants = ordFacade.getRestaurants(user);
        return ResponseEntity.ok(restaurants);
    }
}