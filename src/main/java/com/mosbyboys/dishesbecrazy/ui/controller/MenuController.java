package com.mosbyboys.dishesbecrazy.ui.controller;

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