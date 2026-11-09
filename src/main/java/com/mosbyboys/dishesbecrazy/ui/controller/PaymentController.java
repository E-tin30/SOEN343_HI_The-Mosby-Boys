package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.mosbyboys.dishesbecrazy.application.prc.CheckoutService;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import com.mosbyboys.dishesbecrazy.technical.payment.StripePayments;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final CheckoutService checkouts;
    private final REGFacade users;
    private final StripePayments payments;

    public record CheckoutRequest(UUID checkoutId, UUID restaurantId, List<CheckoutService.Item> items) { }
    public record Cancellation(UUID orderId) { }

    @PostMapping("/create-payment-intent")
    public CheckoutService.Start createPaymentIntent(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestBody CheckoutRequest request) {
        return checkouts.start(users.getCurrentUser(UUID.fromString(jwt.getSubject())),
                request.checkoutId(), request.restaurantId(), request.items());
    }
    // JWT identity and database menu prices determine the stored checkout and authorized amount.

    @PostMapping("/breakdown")
    public PricingBreakdown getBreakdown(@AuthenticationPrincipal Jwt jwt, @RequestBody CheckoutRequest request) {
        return checkouts.quote(users.getCurrentUser(UUID.fromString(jwt.getSubject())),
                request.restaurantId(), request.items());
    }

    @PostMapping("/{checkoutId}/cancel")
    public Cancellation cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID checkoutId) {
        var order = checkouts.cancel(checkoutId, UUID.fromString(jwt.getSubject()));
        return new Cancellation(order == null ? null : order.getId());
    }
    // A completed payment returns its order, so the browser cannot discard it and unknowingly charge again.

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        payments.verifiedCheckout(payload, signature).ifPresent(checkouts::completeFromWebhook);
        return ResponseEntity.ok().build();
    }
    // Signatures replace JWT authentication only on this callback; completion rechecks Stripe's authoritative state.
}
