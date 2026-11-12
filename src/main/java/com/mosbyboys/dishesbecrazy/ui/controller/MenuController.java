package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
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

    public record MenuItemResponse(UUID id, String name, BigDecimal price, String status, String description, int stockQuantity) {
        static MenuItemResponse from(MenuItem m) {
            String availability = (m.isAvailable() ? MenuItemStatus.AVAILABLE : MenuItemStatus.SOLD_OUT).name();
            // Exposes ORD's existing availability rule, including zero stock, in the menu status field.
            return new MenuItemResponse(m.getId(), m.getName(), m.getPrice(), availability, m.getDescription(), m.getStockQuantity());
        }
    }

    public record AddMenuItemRequest(
            @NotBlank String name,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity,
            String description//,
            //String imageUrl
    ) {}

    public record EditMenuItemRequest(
            @NotNull UUID id,
            @NotBlank String name,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity,
            String description//,
            //String imageUrl
    ) {}

    @GetMapping("/api/restaurants/{restaurantId}/menu")
    public ResponseEntity<List<MenuItemResponse>> getMenu(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID restaurantId) {
        User user = regFacade.getCurrentUser(UUID.fromString(jwt.getSubject()));
        List<MenuItem> body = ordFacade.getMenuForRestaurant(user, restaurantId);
        List<MenuItemResponse> menuItems = body.stream()
                .map(MenuItemResponse::from)
                .toList();
        return ResponseEntity.ok(menuItems);
    }

    @PostMapping("/menu/add")
    public ResponseEntity<MenuItem> addMenuItem(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddMenuItemRequest request) {
        UUID restaurantId = regFacade.getCurrentUser(UUID.fromString(jwt.getSubject())).getUserId();

        MenuItemStatus status = request.stockQuantity() > 0
                ? MenuItemStatus.AVAILABLE
                : MenuItemStatus.SOLD_OUT;

        MenuItem item = new MenuItem(
                UUID.randomUUID(), //give it a new random UUID
                restaurantId,
                request.name(),
                request.price(),
                request.stockQuantity(),
                status,
                request.description(),
                "",// temporarily until imageURL is added
                null //createdAt is done automatically so don't need to do it here
        );

        ordFacade.saveMenuItem(item);

        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PostMapping("/menu/edit")
    public ResponseEntity<MenuItem> editMenuItem(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EditMenuItemRequest request) {
        UUID restaurantId = regFacade.getCurrentUser(UUID.fromString(jwt.getSubject())).getUserId();

        MenuItemStatus status = request.stockQuantity() > 0
                ? MenuItemStatus.AVAILABLE
                : MenuItemStatus.SOLD_OUT;

        MenuItem item = new MenuItem(
                request.id(),
                restaurantId,
                request.name(),
                request.price(),
                request.stockQuantity(),
                status,
                request.description(),
                "",// temporarily until imageURL is added
                null //created at. won't be updated anyways
        );

        ordFacade.saveMenuItem(item);

        return ResponseEntity.status(HttpStatus.OK).body(item);
    }
}
