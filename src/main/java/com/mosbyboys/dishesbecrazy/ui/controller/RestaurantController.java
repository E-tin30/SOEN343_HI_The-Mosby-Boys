package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.oauth2.jwt.Jwt;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.reg.User;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
public class RestaurantController {

    private final ORDFacade ordFacade;
    private final REGFacade regFacade;

    public record RestaurantResponse(UUID id, String name, String status) {
    static RestaurantResponse from(Restaurant r) {
        return new RestaurantResponse(r.getUserId(), r.getName(), r.getStatus().name());
    }
}

    @GetMapping("/api/restaurants")
    public ResponseEntity<List<RestaurantResponse>> getRestaurants(@AuthenticationPrincipal Jwt jwt) {
        User user = regFacade.getCurrentUser(UUID.fromString(jwt.getSubject()));
        List<RestaurantResponse> body = ordFacade.getRestaurants(user).stream()
            .map(RestaurantResponse::from)
            .toList();
        return ResponseEntity.ok(body);
    }

    // A restaurant pauses itself, or an operator pauses any restaurant (R-DASH-01)
    @PostMapping("/api/restaurants/{restaurantId}/pause")
    public ResponseEntity<RestaurantResponse> pauseRestaurant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID restaurantId) {
        Restaurant resto = ordFacade.pauseRestaurant(UUID.fromString(jwt.getSubject()), restaurantId);
        return ResponseEntity.ok(RestaurantResponse.from(resto));
    }

    // A restaurant opens itself, or an operator opens any paused or closed restaurant (R-DASH-01)
    @PostMapping("/api/restaurants/{restaurantId}/open")
    public ResponseEntity<RestaurantResponse> openRestaurant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID restaurantId) {
        Restaurant resto = ordFacade.openRestaurant(UUID.fromString(jwt.getSubject()), restaurantId);
        return ResponseEntity.ok(RestaurantResponse.from(resto));
    }
}