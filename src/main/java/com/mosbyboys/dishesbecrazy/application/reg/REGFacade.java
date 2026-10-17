package com.mosbyboys.dishesbecrazy.application.reg;

import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.LoginTokens;

@Service 
public class REGFacade {
    private final AuthService authService;

    public REGFacade(AuthService authService) {
        this.authService = authService;
    }

    public void createUser(String firstName, String lastName, String email, String username, String password, String role) {
        authService.createUser(firstName, lastName, email, username, password, role);
    }

     public LoginTokens login(String email, String password){
        return authService.login(email, password);
    }
}
