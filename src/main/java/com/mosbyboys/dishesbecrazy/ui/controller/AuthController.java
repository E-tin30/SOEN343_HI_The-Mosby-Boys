package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.UUID;
import java.util.NoSuchElementException;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.LoginTokens;
import com.mosbyboys.dishesbecrazy.technical.security.InvalidCredentialsException;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.UserProfile;

@RestController // Tells Spring that this class handles HTTP requests and that its methods
                // return response data
public class AuthController {

    private final REGFacade regFacade;

    public AuthController(REGFacade regFacade) {
        this.regFacade = regFacade;
    }

    public record RegistrationRequest(String firstName, String lastName, String email, String username, String password,
            String role, String address, String restaurantName) {
    }

    public record LoginRequest(String email, String password) {
    }

    @PostMapping("/register")
    public String register(@RequestBody RegistrationRequest request) {
        regFacade.createUser(request.firstName(), request.lastName(), request.email(), request.username(),
                request.password(), request.role(), request.address(), request.restaurantName());
        return "Registration successful";
    }

    @PostMapping("/login")
    public ResponseEntity<LoginTokens> login(@RequestBody LoginRequest request) {
        LoginTokens tokens = regFacade.login(request.email(), request.password());
        return ResponseEntity.ok(tokens);
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<UserProfile> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserProfile user = regFacade.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
    }
}
