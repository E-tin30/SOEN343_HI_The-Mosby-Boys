package com.mosbyboys.dishesbecrazy.application.reg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.technical.repository.UserRepo;

class AuthServiceTest {

    private UserRepo userRepo;
    private Geocoder geocoder;
    private AuthService service;

    private final UUID newUserId = UUID.randomUUID();
    private final Location montreal = new Location(45.5, -73.6);

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepo.class);
        geocoder = mock(Geocoder.class);
        service = new AuthService(userRepo, geocoder);
        when(userRepo.createAuthUser(any(), any(), any())).thenReturn(newUserId);
        when(geocoder.geocode(any())).thenReturn(montreal);
    }

    @Test
    void customerIsGeocodedAndSavedWithItsLocation() {
        service.createUser("Alex", "Example", "a@b.c", "alex", "password1",
                "CUSTOMER", "123 Demo Street", null);

        verify(geocoder).geocode("123 Demo Street");
        verify(userRepo).saveUser(newUserId, "Alex", "Example", "alex", "CUSTOMER",
                "123 Demo Street", null, montreal);
    }

    @Test
    void restaurantIsSavedWithItsNameAndLocation() {
        service.createUser("Rita", "Owner", "r@b.c", "rita", "password1",
                "RESTAURANT", "9 Food Lane", "Demo Diner");

        verify(geocoder).geocode("9 Food Lane");
        verify(userRepo).saveUser(newUserId, "Rita", "Owner", "rita", "RESTAURANT",
                "9 Food Lane", "Demo Diner", montreal);
    }

    @Test
    void courierIsGeocodedAndSavedWithItsLocation() {
        service.createUser("Cory", "Courier", "c@b.c", "cory", "password1",
                "COURIER", "123 Demo Street", null);

        verify(geocoder).geocode("123 Demo Street");
        verify(userRepo).saveUser(newUserId, "Cory", "Courier", "cory", "COURIER",
                "123 Demo Street", null, montreal);
    }

    @Test
    void operatorCannotSelfRegisterAndNothingIsCreated() {
        assertThrows(IllegalArgumentException.class, () ->
                service.createUser("Oscar", "Op", "o@b.c", "oscar", "password1",
                        "OPERATOR", null, null));

        verify(userRepo, never()).createAuthUser(any(), any(), any());
        verify(userRepo, never()).saveUser(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "RESTAURANT", "COURIER"})
    void missingAddressIsRejectedBeforeGeocodingOrCreatingAnyUser(String role) {
        for (String address : new String[]{null, "", " \t "}) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createUser("Alex", "Example", "a@b.c", "alex", "password1",
                            role, address, "Demo Diner"));
            assertEquals("address is required for " + role, exception.getMessage());
        }

        verifyNoInteractions(geocoder, userRepo);
    }

    @Test
    void restaurantWithoutANameIsRejectedBeforeAnyLoginIsCreated() {
        assertThrows(IllegalArgumentException.class, () ->
                service.createUser("Rita", "Owner", "r@b.c", "rita", "password1",
                        "RESTAURANT", "9 Food Lane", " "));

        verify(userRepo, never()).createAuthUser(any(), any(), any());
    }

    @Test
    void unknownRoleIsRejectedBeforeAnyLoginIsCreated() {
        assertThrows(IllegalArgumentException.class, () ->
                service.createUser("Alex", "Example", "a@b.c", "alex", "password1",
                        "WIZARD", "123 Demo Street", null));

        verify(userRepo, never()).createAuthUser(any(), any(), any());
    }

    @Test
    void loginIsPassedStraightToTheRepo() {
        UserRepo.LoginTokens tokens = new UserRepo.LoginTokens("a", "r", 3600);
        when(userRepo.login("a@b.c", "password1")).thenReturn(tokens);

        assertSame(tokens, service.login("a@b.c", "password1"));
    }

    @Test
    void missingProfileThrowsNoSuchElement() {
        when(userRepo.findUserById(any())).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.getUserById(UUID.randomUUID()));
    }
}
