package com.mosbyboys.dishesbecrazy.application.reg;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.technical.repository.AccountRepo;

@Service
public class AuthService{

    private final AccountRepo accountRepo;

    public AuthService(AccountRepo accountRepo) {
        this.accountRepo = accountRepo;
    }
    
    public void testSaveAccount() {
        UUID id = UUID.randomUUID();
        String username = "ABC";
        String role = "customer";

        int rows = accountRepo.saveAccount(id, username, role);
        
        System.out.println("Rows inserted: " + rows);
    }
}
