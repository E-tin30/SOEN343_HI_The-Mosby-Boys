package com.mosbyboys.dishesbecrazy.application.reg;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.technical.repository.AccountRepo;

@Service
public class AuthService {

    private final AccountRepo accountRepo;

    public AuthService(AccountRepo accountRepo) {
        this.accountRepo = accountRepo;
    }

    public void saveAccount(String firstName, String lastName, String email, String username, String password, String role) {
        firstName = "Zayden";
        lastName = "Kungu";
        email = "zayden1919@gmail.com";
        username = "Zayy";
        password = "password123";
        role = "customer";

        int rows = accountRepo.saveUserAccount(accountRepo.createAuthUser(firstName + " " + lastName, email, password),
                firstName, lastName, username, role);
    }
}
