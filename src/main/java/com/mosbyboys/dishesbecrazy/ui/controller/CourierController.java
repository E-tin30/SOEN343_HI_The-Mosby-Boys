package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.UUID;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.mosbyboys.dishesbecrazy.application.dlv.DLVFacade;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;

import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.application.dlv.CourierData;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/courier")
@RequiredArgsConstructor
public class CourierController {

    private final DLVFacade dlvFacade;
    private final REGFacade regFacade;

    public record PendingOfferResponse(UUID orderId, UUID restaurantId) {

    }

    public record CourierStateResponse(CourierState state) {

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

    //The logged-in courier's own state (OFFLINE, IDLE, ASSIGNED or CARRYING).
    @GetMapping("/state")
    public ResponseEntity<CourierStateResponse> getState(@AuthenticationPrincipal Jwt jwt) {
        UUID courierId = getCourierId(jwt);

        return ResponseEntity.ok(new CourierStateResponse(dlvFacade.getCourierState(courierId)));
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

    //Courier goes online (OFFLINE -> IDLE).
    @PostMapping("/online")
    public ResponseEntity<Void> goOnline(@AuthenticationPrincipal Jwt jwt) {
        UUID courierId = getCourierId(jwt);
        dlvFacade.goOnline(courierId);

        return ResponseEntity.noContent().build();
    }

    //Courier goes offline, implicitly rejecting any pending offer.     
    @PostMapping("/offline")
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
        dlvFacade.confirmPickup(courierId, orderId);

        return ResponseEntity.noContent().build();
    }

    //Courier confirms delivery (PICKED_UP -> DELIVERED).
    @PostMapping("/orders/{orderId}/deliver")
    public ResponseEntity<Void> confirmDelivery(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID orderId) {
        UUID courierId = getCourierId(jwt);
        dlvFacade.confirmDelivery(courierId, orderId);

        return ResponseEntity.noContent().build();
    }

    private void checkOperator(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        if (regFacade.getCurrentUser(userId).getRole() != Role.OPERATOR) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only operators can perform this action"
            );
        }
    }

    @GetMapping("/{courierId}/idle")
    public ResponseEntity<List<CourierData>> getOtherIdleCouriers(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courierId) {
        checkOperator(jwt);
        return ResponseEntity.ok(dlvFacade.getOtherIdleCouriers(courierId));
    }

    @PostMapping("/{oldCourierId}/swap/{newCourierId}")
    public ResponseEntity<Void> swapCourier(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID oldCourierId,@PathVariable UUID newCourierId) {
        checkOperator(jwt);

        dlvFacade.reassignCourier(oldCourierId, newCourierId);

        return ResponseEntity.noContent().build();
    }

}
