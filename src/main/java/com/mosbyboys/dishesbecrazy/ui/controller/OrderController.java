package com.mosbyboys.dishesbecrazy.ui.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.application.ord.RestaurantOrderView;
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
            return new OrderResponse(o.getId(), o.getRestaurantId(), o.getState().toString(), o.getSubtotal());
        }
    }

    public record OrderLineResponse(UUID menuItemId, String name, int quantity, BigDecimal unitPrice) {}

    // status is the state without the "State" suffix ("PreparingState" -> "PREPARING"), which is what the frontend uses.
    public record RestaurantOrderResponse(UUID id, String status, Instant placedAt, List<OrderLineResponse> lines,
                                          BigDecimal total, boolean nextToAccept, boolean nextToPrepare) {
        static RestaurantOrderResponse from(RestaurantOrderView view) {
            Order o = view.order();
            List<OrderLineResponse> lines = o.getOrderLineItems().stream()
                    .map(l -> new OrderLineResponse(l.getMenuItemId(),
                            view.itemNames().getOrDefault(l.getMenuItemId(), "Unknown item"),
                            l.getQuantity(), l.getUnitPrice()))
                    .toList();
            String status = o.getState().toString().replace("State", "").toUpperCase();
            return new RestaurantOrderResponse(o.getId(), status, o.getPlacedAt(), lines, o.getSubtotal(),
                    view.nextToAccept(), view.nextToPrepare());
        }
    }

    // The logged-in restaurant's orders that are still in one of its queues, oldest first.
    @GetMapping("/api/restaurant/orders")
    public ResponseEntity<List<RestaurantOrderResponse>> getRestaurantOrders(@AuthenticationPrincipal Jwt jwt) {
        User user = regFacade.getCurrentUser(UUID.fromString(jwt.getSubject()));
        List<RestaurantOrderResponse> body = ordFacade.getRestaurantOrders(user).stream()
                .map(RestaurantOrderResponse::from)
                .toList();
        return ResponseEntity.ok(body);
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

    public record RejectOrderRequest(String reason) {}

    @PostMapping("/api/orders/{orderId}/accept")
    public ResponseEntity<Void> acceptOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        ordFacade.acceptOrder(UUID.fromString(jwt.getSubject()), orderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/orders/{orderId}/reject")
    public ResponseEntity<Void> rejectOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                                            @RequestBody RejectOrderRequest request) {
        ordFacade.rejectOrder(UUID.fromString(jwt.getSubject()), orderId, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/orders/{orderId}/prepare")
    public ResponseEntity<Void> prepareOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        ordFacade.prepareOrder(UUID.fromString(jwt.getSubject()), orderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/orders/{orderId}/ready")
    public ResponseEntity<Void> markOrderReady(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        ordFacade.markOrderReady(UUID.fromString(jwt.getSubject()), orderId);
        return ResponseEntity.noContent().build();
    }
}