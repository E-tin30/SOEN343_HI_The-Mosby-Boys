package com.mosbyboys.dishesbecrazy.domain.reg;

import com.mosbyboys.dishesbecrazy.domain.reg.UserFactory.UserData;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserFactoryTests {
    private final UserFactory factory = new UserFactory();
    private final UUID userId = UUID.randomUUID();
    private final OffsetDateTime createdAt = OffsetDateTime.parse("2026-10-19T10:00:00-04:00");

    private UserData.UserDataBuilder sharedData() {
        return UserData.builder().userId(userId).createdAt(createdAt)
                .firstName("Alex").lastName("Example").email("alex@example.com")
                .username("alex").hashedPassword("already-hashed-test-value");
    }

    private void assertSharedFields(User user, Role role) {
        assertAll(
                () -> assertEquals(userId, user.getUserId()),
                () -> assertEquals(createdAt, user.getCreatedAt()),
                () -> assertEquals("Alex", user.getFirstName()),
                () -> assertEquals("Example", user.getLastName()),
                () -> assertEquals("alex@example.com", user.getEmail()),
                () -> assertEquals("alex", user.getUsername()),
                () -> assertEquals(role, user.getRole()));
    }

    @Test
    void createsCustomerWithItsProfile() {
        var data = sharedData().address("123 Demo Street").build();

        var customer = assertInstanceOf(Customer.class, factory.createUser(Role.CUSTOMER, data));

        assertSharedFields(customer, Role.CUSTOMER);
        assertEquals("123 Demo Street", customer.getAddress());
    }

    @Test
    void createsCourierWithItsProfile() {
        var location = new Location(45.5017, -73.5673);
        var data = sharedData().state(CourierState.OFFLINE).location(location).build();

        var courier = assertInstanceOf(Courier.class, factory.createUser(Role.COURIER, data));

        assertSharedFields(courier, Role.COURIER);
        assertEquals(CourierState.OFFLINE, courier.getState());
        assertSame(location, courier.getLocation());
    }

    @Test
    void createsRestaurant() {
        var location = new Location(45.5017, -73.5673);
        var data = sharedData().restaurantName("Demo Diner").status(RestaurantStatus.OPEN).location(location).build();

        var restaurant = assertInstanceOf(Restaurant.class, factory.createUser(Role.RESTAURANT, data));

        assertSharedFields(restaurant, Role.RESTAURANT);
        assertEquals("Demo Diner", restaurant.getName());
        assertEquals(RestaurantStatus.OPEN, restaurant.getStatus());
        assertSame(location, restaurant.getLocation());
    }

    @Test
    void createsOperatorWithoutASeparateProfile() {
        var operator = assertInstanceOf(Operator.class,
                factory.createUser(Role.OPERATOR, sharedData().build()));

        assertSharedFields(operator, Role.OPERATOR);
    }

    @Test
    void rejectsMissingRoleOrData() {
        assertThrows(NullPointerException.class, () -> factory.createUser(null, sharedData().build()));
        assertThrows(NullPointerException.class, () -> factory.createUser(Role.CUSTOMER, null));
    }

    @Test
    void dataToStringDoesNotExposePasswordHash() {
        assertFalse(sharedData().build().toString().contains("already-hashed-test-value"));
        assertFalse(sharedData().toString().contains("already-hashed-test-value"));
    }
}
