package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

}