package com.mosbyboys.dishesbecrazy.ui.controller;

@AllArgeCOnstructor
@RestController
public class RestaurantController {

    private final ORDFacade ordFacade;

    @GetMapping("/api/restaurants")
    public ResponseEntity<List<Restaurant>> getRestaurants(User user) {
        List<Restaurant> restaurants = ordFacade.getRestaurants(user);
        return ResponseEntity.ok(restaurants);
    }
}