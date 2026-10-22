package com.mosbyboys.dishesbecrazy.application.reg;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;


    public void createUser(String firstName, String lastName, String email, String username, String password, String role, 
        String address, String restaurantName) {
        authService.createUser(firstName, lastName, email, username, password, role, address, restaurantName);
    }

    public LoginTokens login(String email, String password) {
        return authService.login(email, password);
    }

    public UserProfile getUserById(UUID id) {
        return authService.getUserById(id);
    }

    public User getCurrentUser(UUID userId) {
        UserProfile p = authService.getUserById(userId);
        Role role = Role.valueOf(p.role().toUpperCase());
        var b = UserFactory.UserData.builder()
                .userId(p.id())
                .firstName(p.firstName())
                .lastName(p.lastName())
                .username(p.username());
        switch (role) {
            case CUSTOMER -> jdbcTemplate.queryForObject(
                    "SELECT created_at, address FROM \"Customer\" WHERE id = ?",
                    (rs, i) -> b
                            .address(rs.getString("address")),
                    userId);
            case COURIER -> jdbcTemplate.queryForObject(
                    "SELECT state FROM \"Courier\" WHERE id = ?",
                    (rs, i) -> b
                            .state(rs.getString("state")),
                    userId);
            case RESTAURANT -> jdbcTemplate.queryForObject(
                    "SELECT name, status FROM \"Restaurant\" WHERE id = ?",
                    (rs, i) -> b
                            .restaurantName(rs.getString("name"))
                            .status(rs.getString("status")),
                    userId);
        }
        return userFactory.createUser(role, b.build());
    }
}
