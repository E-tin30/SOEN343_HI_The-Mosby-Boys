package com.mosbyboys.dishesbecrazy.application.reg;

import java.util.UUID;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.LoginTokens;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo.UserProfile;

@Service
public class AuthService {

    private final UserRepo userRepo;
    private final Geocoder geocoder;

    public AuthService(UserRepo userRepo, Geocoder geocoder) {
        this.userRepo = userRepo;
        this.geocoder = geocoder;
    }

    public void createUser(String firstName, String lastName, String email, String username, String password, String role, String address, String restaurantName) {
        Role userRole = Role.valueOf(role);
        boolean needsAddress = userRole == Role.CUSTOMER || userRole == Role.RESTAURANT;
        
        if (needsAddress && (address == null || address.isBlank())) {
            throw new IllegalArgumentException("address is required for " + userRole);
        }
        if (userRole == Role.RESTAURANT && (restaurantName == null || restaurantName.isBlank())) {
            throw new IllegalArgumentException("restaurantName is required for RESTAURANT");
        }

        Location location = needsAddress ? geocoder.geocode(address) : null;

        UUID id = userRepo.createAuthUser(firstName + " " + lastName, email, password);
        userRepo.saveUser(id, firstName, lastName, username, userRole.name(), address, restaurantName, location);
    }

    public LoginTokens login(String email, String password){
        return userRepo.login(email, password);
    }

    public UserProfile getUserById(UUID id){
        return userRepo.findUserById(id).orElseThrow(() -> new NoSuchElementException("User profile not found"));
    }

}
