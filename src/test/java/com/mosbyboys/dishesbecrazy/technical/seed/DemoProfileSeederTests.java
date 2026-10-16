package com.mosbyboys.dishesbecrazy.technical.seed;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Uses mocks so tests never contact Supabase.
class DemoProfileSeederTests {
    private final DemoAuthAdmin auth = mock(DemoAuthAdmin.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

    // Builds the seeder with simulated external services.
    private DemoProfileSeeder seeder() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new DemoProfileSeeder(jdbc, manager, JsonMapper.builder().build(), auth);
    }

    // A failed Auth creation must prevent all profile inserts.
    @Test
    void failedFourthAuthCreationPreventsAllProfileWrites() {
        var seeder = seeder();
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(Object[].class)))
                .thenReturn(List.of(UUID.randomUUID()));
        when(jdbc.queryForList(anyString(), eq(UUID.class), eq("operator.demo@example.com")))
                .thenReturn(List.of());

        when(auth.create(any())).thenThrow(new IllegalStateException("Admin API unavailable"));

        // Checks the failure and confirms that nothing is committed.
        assertThrows(IllegalStateException.class, seeder::run);

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(manager, never()).getTransaction(any());
        verify(manager, never()).commit(any());
    }

    // A role mismatch must roll back instead of changing permissions.
    @Test
    void existingDifferentRoleRollsBackInsteadOfReassigningIt() {
        var seeder = seeder();
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(Object[].class)))
                .thenReturn(List.of(UUID.randomUUID()));
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("OPERATOR");

        // Checks the failure and confirms that nothing is committed.
        assertThrows(IllegalStateException.class, seeder::run);

        verifyNoInteractions(auth);
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }
}
