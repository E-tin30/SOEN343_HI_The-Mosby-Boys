package com.mosbyboys.dishesbecrazy.application.reg;

import java.util.UUID;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.mosbyboys.dishesbecrazy.domain.reg.UserFactory;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.LoginTokens;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.UserProfile;

@Service
@RequiredArgsConstructor 
public class REGFacade {
    private final AuthService authService;
    private final UserFactory userFactory = new UserFactory();


    public void createUser(String firstName, String lastName, String email, String username, String password, String role) {
        authService.createUser(firstName, lastName, email, username, password, role);
    }

     public LoginTokens login(String email, String password){
        return authService.login(email, password);
    }

    public UserProfile getUserById(UUID id) {
        return authService.getUserById(id);
    }

    public User getCurrentUser(UUID id) {
        UserProfile p = authService.getUserById(id);
        Role role = Role.valueOf(p.role().toUpperCase());
        return userFactory.createUser(role, UserFactory.UserData.builder()
            .userId(p.id())
            .firstName(p.firstName())
            .lastName(p.lastName())
            .username(p.username())
            .build());
}
}
