package com.mosbyboys.dishesbecrazy.application.reg;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.LoginTokens;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.UserProfile;

@Service 
public class REGFacade {
    private final AuthService authService;

    public REGFacade(AuthService authService) {
        this.authService = authService;
    }

    public void createUser(String firstName, String lastName, String email, String username, String password, String role, 
        String address, String restaurantName) {
        authService.createUser(firstName, lastName, email, username, password, role, address, restaurantName);
    }

     public LoginTokens login(String email, String password){
        return authService.login(email, password);
    }

    public UserProfile getUserById(UUID id) {
        return authService.getUserById(id);
    }
}
