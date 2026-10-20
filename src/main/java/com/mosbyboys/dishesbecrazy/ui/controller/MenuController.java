package com.mosbyboys.dishesbecrazy.ui.controller;

@RestController
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/api/restaurants")
    public ResponseEntity<List<Restaurant>> getRestaurants() {
        List<Restaurant> restaurants = menuService.getRestaurants();
        return ResponseEntity.ok(restaurants);
    }

    @GetMapping("/api/restaurants/{restaurantId}/menu")
    public ResponseEntity<List<MenuItem>> getMenu(@PathVariable UUID restaurantId) {
        List<MenuItem> menuItems = menuService.getMenuForRestaurant(restaurantId);
        return ResponseEntity.ok(menuItems);
    }
}