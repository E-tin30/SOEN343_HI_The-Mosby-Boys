package com.mosbyboys.dishesbecrazy.ui.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mosbyboys.dishesbecrazy.application.dash.ResetService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class DemoResetController {
    private final ResetService reset;

    @PostMapping("/api/demo/reset")
    public ResponseEntity<Void> reset(@AuthenticationPrincipal Jwt jwt) throws IOException {
        reset.restoreDatabaseAndOrderQueues(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
    // Use the authenticated identity; ResetService checks the persisted role and completes restoration before success.
}
