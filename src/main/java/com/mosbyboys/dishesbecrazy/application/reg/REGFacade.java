package com.mosbyboys.dishesbecrazy.application.reg;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.mosbyboys.dishesbecrazy.application.ord.RestaurantService;
import com.mosbyboys.dishesbecrazy.domain.reg.UserFactory;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.LoginTokens;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.UserProfile;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;

@Service
@RequiredArgsConstructor
public class REGFacade {
    private final AuthService authService;
    private final RestaurantService restaurantService;
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

    public Map<UUID, String> getDisplayNames(Collection<UUID> userIds) {
        return authService.getDisplayNames(userIds);
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
                    "SELECT created_at, address FROM \"Customer\" WHERE user_id = ?",
                    (rs, i) -> b
                            .address(rs.getString("address")),
                    userId);
            case COURIER -> jdbcTemplate.queryForObject(
                    "SELECT state FROM \"Courier\" WHERE user_id = ?",
                    (rs, i) -> b
                            .state(CourierState.valueOf(rs.getString("state"))),
                    userId);
            case RESTAURANT -> jdbcTemplate.queryForObject(
                    "SELECT name, status FROM \"Restaurant\" WHERE user_id = ?",
                    (rs, i) -> b
                            .restaurantName(rs.getString("name"))
                            .status(RestaurantStatus.valueOf(rs.getString("status"))),
                    userId);
        }
//        if (role == Role.RESTAURANT) {
//            // A restaurant account is its Restaurant; RestaurantService loads it with its User fields.
//            return restaurantService.load(id);
//        }
        return userFactory.createUser(role, b.build());
    }
}
