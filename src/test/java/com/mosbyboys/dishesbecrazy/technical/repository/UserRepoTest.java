package com.mosbyboys.dishesbecrazy.technical.repository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mosbyboys.dishesbecrazy.domain.ord.Location;

class UserRepoTest {

    @Test
    void courierIsInsertedWithItsCoordinatesAndOfflineState() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UserRepo repo = new UserRepo(jdbc, "https://example.invalid", "test-key");
        UUID userId = UUID.randomUUID();

        repo.saveUser(userId, "Cory", "Courier", "cory", "COURIER",
                "123 Demo Street", null, new Location(45.5, -73.6));

        verify(jdbc).update(
                "INSERT INTO public.\"Courier\" (user_id, created_at, state, latitude, longitude) "
                        + "VALUES (?, CURRENT_TIMESTAMP, 'OFFLINE', ?, ?)",
                userId, 45.5, -73.6);
    }
}
