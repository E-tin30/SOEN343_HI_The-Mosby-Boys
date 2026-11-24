package com.mosbyboys.dishesbecrazy.ui.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import com.mosbyboys.dishesbecrazy.application.ord.MenuImageService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
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

    public record MenuItemResponse(UUID id, String name, BigDecimal price, String status, String description, String postDescription, int stockQuantity, String itemUrl) {
        static MenuItemResponse from(MenuItem m) {
            String availability = (m.isAvailable() ? MenuItemStatus.AVAILABLE : MenuItemStatus.SOLD_OUT).name();
            // Exposes ORD's existing availability rule, including zero stock, in the menu status field.
            return new MenuItemResponse(m.getId(), m.getName(), m.getPrice(), availability, m.getDescription(), m.getPostDescription(), m.getStockQuantity(), m.getImageUrl());
        }
    }

    public record EditMenuItemRequest(
            @NotNull UUID id,
            @NotBlank String name,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity,
            String description,
            String postDescription,
            String imageUrl
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

    @PostMapping(value = "/menu/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MenuItem> addMenuItem(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String name,
            @RequestParam BigDecimal price,
            @RequestParam Integer stockQuantity,
            @RequestParam String description,
            @RequestParam String postDescription, //TODO: maybe remove if not posting directly when adding an item
            @RequestPart(required = false) MultipartFile image
    ) {

        UUID restaurantId = regFacade
                .getCurrentUser(UUID.fromString(jwt.getSubject()))
                .getUserId();

        MenuItemStatus status = stockQuantity > 0
                ? MenuItemStatus.AVAILABLE
                : MenuItemStatus.SOLD_OUT;

        UUID menuItemId = UUID.randomUUID();

        String imageUrl = null;

        if (image != null && !image.isEmpty()) {
            try {
                imageUrl = ordFacade.saveMenuItemImage(
                        menuItemId,
                        image.getBytes(),
                        image.getContentType()
                );
            } catch (IOException e) {
                imageUrl = null;
            }
        }

        MenuItem item = new MenuItem(
                menuItemId,
                restaurantId,
                name,
                price,
                stockQuantity,
                status,
                description,
                postDescription,
                imageUrl,
                null
        );

        ordFacade.saveMenuItem(item);

        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PostMapping("/menu/edit")
    public ResponseEntity<MenuItem> editMenuItem(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EditMenuItemRequest request) {
        UUID restaurantId = regFacade.getCurrentUser(UUID.fromString(jwt.getSubject())).getUserId();

        //check if the restaurant is the correct one calling and also ensures that the menu item exists already
        if(restaurantId == null || !restaurantId.equals(ordFacade.getRestaurantId(request.id()))) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(null);
        }

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
                request.postDescription(),
                request.imageUrl(),
                null //created at. won't be updated anyway
        );

        ordFacade.saveMenuItem(item);

        return ResponseEntity.status(HttpStatus.OK).body(item);
    }
}
