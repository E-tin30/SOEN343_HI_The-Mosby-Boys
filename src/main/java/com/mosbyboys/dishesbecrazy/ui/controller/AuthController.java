package com.mosbyboys.dishesbecrazy.ui.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mosbyboys.dishesbecrazy.application.reg.AuthService;

@RestController //Tells Spring that this class handles HTTP requests and that its methods return response data
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

	public record RegistrationRequest(String firstName, String lastName, String email, String username, String password, String role){	
	}

    @PostMapping("/register")
    public String register(@RequestBody RegistrationRequest request) {
		authService.saveAccount(request.firstName(), request.lastName(), request.email(), request.username(), request.password(),request.role());
        return "Registration successful";
    }
}
