package com.mosbyboys.dishesbecrazy.application.reg;

import org.springframework.stereotype.Service;

@Service 
public class REGFacade {
    private final AuthService authService;

    public REGFacade(AuthService authService) {
        this.authService = authService;
    }

    public void createUser(String firstName, String lastName, String email, String username, String password, String role) {
        authService.saveUser(firstName, lastName, email, username, password, role);
    }
}
