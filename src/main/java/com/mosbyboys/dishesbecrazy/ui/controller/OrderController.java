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
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.application.ord.OrderHistoryView;

import lombok.RequiredArgsConstructor;

/* Takes HTTP requests, calls one ORDFacade method, returns the result.
No business logic here. The caller always comes from the JWT (R-REG-02). */
@RequiredArgsConstructor
@RestController
public class OrderController {

    private final ORDFacade ordFacade;
    private final REGFacade regFacade;

    public record OrderResponse(UUID id, UUID restaurantId, String state, BigDecimal subtotal, List<OrderLine> orderLines) {
        static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getRestaurantId(), o.getState().toString(), o.getSubtotal(), o.getOrderLineItems());
        }
    }

    public record OrderLineResponse(UUID menuItemId, String menuItemName, int quantity, BigDecimal unitPrice) {}

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
                .map(item -> new OrderLine(item.menuItemId(), item.quantity(), item.price(), item.menuItemName()))
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

    @GetMapping("/api/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable UUID orderId) {

        Order order = ordFacade.getOrder(orderId);

        return ResponseEntity.ok(OrderResponse.from(order));
    }

    // One row of the history page. status is the state without "State" ("DeliveredState" -> "DELIVERED").
    // total is the item subtotal. rejectionReason is null unless the order was rejected, customerName is
    // null unless the caller is an operator or restaurant, and pricing is null only for old orders
    // stored without a subtotal.
    public record OrderHistoryResponse(UUID id, UUID restaurantId, String restaurantName, String customerName,
                                    String status, Instant placedAt, List<OrderLineResponse> lines,
                                    BigDecimal total, PricingBreakdown pricing, String rejectionReason) {
        static OrderHistoryResponse from(OrderHistoryView view) {
            Order o = view.order();
            List<OrderLineResponse> lines = o.getOrderLineItems().stream()
                    .map(l -> new OrderLineResponse(l.getMenuItemId(),
                            view.itemNames().getOrDefault(l.getMenuItemId(), "Unknown item"),
                            l.getQuantity(), l.getUnitPrice()))
                    .toList();
            String status = o.getState().toString().replace("State", "").toUpperCase();
            return new OrderHistoryResponse(
                    o.getId(),
                    o.getRestaurantId(),
                    view.restaurantNames().getOrDefault(o.getRestaurantId(), "Unknown restaurant"),
                    view.customerNames().get(o.getCustomerId()),
                    status, o.getPlacedAt(), lines,
                    o.getSubtotal(), view.pricing(), o.getRejectionReason());
        }
    }

    // The logged-in courier's offered, accepted and in-progress orders, in the same shape as history.
    @GetMapping("/api/orders/courier/current")
    public ResponseEntity<List<OrderHistoryResponse>> getCourierCurrentOrders(@AuthenticationPrincipal Jwt jwt) {
        User user = regFacade.getCurrentUser(UUID.fromString(jwt.getSubject()));
        List<OrderHistoryResponse> body = ordFacade.getCourierCurrentOrders(user).stream()
                .map(OrderHistoryResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    // The logged-in user's order history. What they see depends on their role (R-PRC-04).
    @GetMapping("/api/orders/history")
    public ResponseEntity<List<OrderHistoryResponse>> getOrderHistory(@AuthenticationPrincipal Jwt jwt) {
        User user = regFacade.getCurrentUser(UUID.fromString(jwt.getSubject()));
        List<OrderHistoryResponse> body = ordFacade.getOrderHistory(user).stream()
                .map(OrderHistoryResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    // Customer cancels a placed order, or an operator cancels one (checked by PermissionPolicy).
    @PostMapping("/api/orders/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        ordFacade.cancelOrder(UUID.fromString(jwt.getSubject()), orderId);
        return ResponseEntity.noContent().build();
    }

    // Operator refunds a delivered order.
    @PostMapping("/api/orders/{orderId}/refund")
    public ResponseEntity<Void> refundOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        ordFacade.refundOrder(UUID.fromString(jwt.getSubject()), orderId);
        return ResponseEntity.noContent().build();
    }
}