package com.mosbyboys.dishesbecrazy.application.reg;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo;

@Service
public class AuthService {

    private final UserRepo userRepo;

    public AuthService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    public void saveUser(String firstName, String lastName, String email, String username, String password, String role) {
        userRepo.saveUser(userRepo.createAuthUser(firstName + " " + lastName, email, password), firstName, lastName, username, role);
    }
}
