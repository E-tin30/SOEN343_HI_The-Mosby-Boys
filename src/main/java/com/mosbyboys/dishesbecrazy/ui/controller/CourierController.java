package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.mosbyboys.dishesbecrazy.application.dlv.DLVFacade;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/courier")
@RequiredArgsConstructor
public class CourierController {

    private final DLVFacade dlvFacade;
    private final REGFacade regFacade;
    private final ORDFacade ordFacade;

    public record PendingOfferResponse(UUID orderId, UUID restaurantId) {

    }

    private UUID getCourierId(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        if (regFacade.getCurrentUser(userId).getRole() != Role.COURIER) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only couriers can perform this action"
            );
        }

        return userId;
    }

    @GetMapping("/offers/pending")
    public ResponseEntity<PendingOfferResponse> getPendingOffer(@AuthenticationPrincipal Jwt jwt) {
        //Identify the logged-in courier
        UUID courierId = UUID.fromString(jwt.getSubject());

        //Verify the user is a courier
        if (regFacade.getCurrentUser(courierId).getRole() != Role.COURIER) {
            return ResponseEntity.status(403).build();
        }

        //Ask DLV for the courier's pending offer
        Order order = dlvFacade.getPendingOffer(courierId);

        //No pending offer
        if (order == null) {
            return ResponseEntity.noContent().build();
        }

        //Return the offer
        return ResponseEntity.ok(new PendingOfferResponse(order.getId(),order.getRestaurantId()));
    }

    //Courier goes on duty (OFFLINE -> IDLE).
    @PostMapping("/on-duty")
    public ResponseEntity<Void> goOnDuty(@AuthenticationPrincipal Jwt jwt) {
        UUID courierId = getCourierId(jwt);
        dlvFacade.goOnDuty(courierId);

        return ResponseEntity.noContent().build();
    }

    //Courier goes offline, implicitly rejecting any pending offer.     
    @PostMapping("/off-duty")
    public ResponseEntity<Void> goOffline(@AuthenticationPrincipal Jwt jwt) {
        UUID courierId = getCourierId(jwt);
        dlvFacade.goOffline(courierId);

        return ResponseEntity.noContent().build();
    }

    //Courier accepts an offered order.
    @PostMapping("/offers/{orderId}/accept")
    public ResponseEntity<Void> acceptOffer(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID orderId) {
        UUID courierId = getCourierId(jwt);
        dlvFacade.acceptOffer(courierId, orderId);

        return ResponseEntity.noContent().build();
    }

    //Courier rejects an offered order and goes offline.
    @PostMapping("/offers/{orderId}/reject")
    public ResponseEntity<Void> rejectOffer(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID orderId) {
        UUID courierId = getCourierId(jwt);
        dlvFacade.goOffline(courierId);

        return ResponseEntity.noContent().build();
    }

    //Courier confirms pickup (READY -> PICKED_UP).
    @PostMapping("/orders/{orderId}/pickup")
    public ResponseEntity<Void> confirmPickup(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID orderId) {
        UUID courierId = getCourierId(jwt);
        
        ordFacade.pickUpOrder(courierId, orderId);
        dlvFacade.carryDelivery(courierId);

        return ResponseEntity.noContent().build();
    }

    //Courier confirms delivery (PICKED_UP -> DELIVERED).
    @PostMapping("/orders/{orderId}/deliver")
    public ResponseEntity<Void> confirmDelivery(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID orderId) {
        UUID courierId = getCourierId(jwt);
        
        dlvFacade.confirmDelivery(courierId, orderId);
        ordFacade.deliverOrder(courierId, orderId);

        return ResponseEntity.noContent().build();
    }

}
