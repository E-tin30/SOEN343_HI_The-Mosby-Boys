package com.mosbyboys.dishesbecrazy.ui.controller;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/* Takes HTTP requests, calls one OrderService method, returns the result.
No business logic here. */
@Slf4j
@RequiredArgsConstructor
@RestController
public class OrderController {

    private final ORDFacade ordFacade;

    @PostMapping("/placeOrder")
    public ResponseEntity<Order> placeOrder(
            @RequestBody PlaceOrderRequest request) {
        List<OrderLine> orderLines = request.items().stream()
                .map(item -> new OrderLine(
                        item.menuItemId(),
                        item.quantity(),
                        item.price()
                ))
                .toList();
        Order order = ordFacade.placeOrder(
                request.userId(),
                request.restaurantId(),
                orderLines
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(order);
    }
}
