package com.mosbyboys.dishesbecrazy.technical.seed;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import com.mosbyboys.dishesbecrazy.domain.reg.Role;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class DemoFixtureLoaderTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final DemoFixtureLoader loader = new DemoFixtureLoader(json);

    @Test
    void loadsExistingProfilesAndMenuWithoutExternalServices() throws Exception {
        DemoFixtures fixtures = loader.load();

        assertEquals(4, fixtures.accounts().size());
        assertEquals(Role.CUSTOMER, fixtures.accounts().getFirst().role());
        assertEquals("demo_customer", fixtures.accounts().getFirst().username());
        assertEquals(4, fixtures.menuItems().size());
        assertEquals(new BigDecimal("18.50"), fixtures.menuItems().getFirst().price());
        assertEquals("demo_restaurant", fixtures.menuItems().getFirst().restaurantUsername());
    }
    // Exercises the actual checked-in JSON without provisioning accounts or starting Spring.

    @Test
    void rejectsAnEmptyMenuBeforeReturningFixtures() {
        var profiles = new ClassPathResource("demo/profiles.json");
        var menu = new ByteArrayResource("[]".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var error = assertThrows(IllegalArgumentException.class, () -> loader.load(profiles, menu));

        assertEquals("Menu seed configuration has no items.", error.getMessage());
    }
    // A caller cannot begin profile seeding with a menu that failed preflight validation.
}
