package com.mosbyboys.dishesbecrazy.ui.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.reg.User;

import lombok.RequiredArgsConstructor;

/* Takes HTTP requests, calls one ORDFacade method, returns the result.
No business logic here. The caller always comes from the JWT (R-REG-02). */
@RequiredArgsConstructor
@RestController
public class OrderController {

    private final ORDFacade ordFacade;
    private final REGFacade regFacade;

    public record OrderResponse(UUID id, UUID restaurantId, String state, BigDecimal total) {
        static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getRestaurantId(), o.getState().toString(), o.getTotal());
        }
    }

    @PostMapping("/placeOrder")
    public ResponseEntity<OrderResponse> placeOrder(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestBody PlaceOrderRequest request) {
        User user = regFacade.getCurrentUser(UUID.fromString(jwt.getSubject()));

        List<OrderLine> orderLines = request.items().stream()
                .map(item -> new OrderLine(item.menuItemId(), item.quantity(), item.price()))
                .toList();

        Order order = ordFacade.placeOrder(user, request.restaurantId(), orderLines);

        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }
}