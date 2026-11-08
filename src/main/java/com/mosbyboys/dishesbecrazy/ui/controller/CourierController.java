package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mosbyboys.dishesbecrazy.application.dlv.DLVFacade;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CourierController {

    private final DLVFacade dlvFacade;
    private final REGFacade regFacade;

    public record PendingOfferResponse(UUID orderId, UUID restaurantId) {

    }

    @GetMapping("/api/courier/offers/pending")
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
}
