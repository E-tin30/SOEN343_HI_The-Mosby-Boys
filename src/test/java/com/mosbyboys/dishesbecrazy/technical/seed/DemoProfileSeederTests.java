package com.mosbyboys.dishesbecrazy.technical.seed;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.mosbyboys.dishesbecrazy.domain.reg.Role;

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
        return seeder(new DemoFixtureLoader(JsonMapper.builder().build()));
    }

    private DemoProfileSeeder seeder(DemoFixtureLoader fixtures) {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new DemoProfileSeeder(jdbc, manager, fixtures, auth);
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

    @Test
    void invalidMenuPreventsAuthAndAllDatabaseAccess() throws Exception {
        var loader = spy(new DemoFixtureLoader(JsonMapper.builder().build()));
        doAnswer(invocation -> loader.load(new ClassPathResource("demo/profiles.json"),
                new ByteArrayResource("[]".getBytes(StandardCharsets.UTF_8)))).when(loader).load();
        var seeder = seeder(loader);

        assertThrows(IllegalArgumentException.class, seeder::run);

        verifyNoInteractions(auth, jdbc, manager);
    }
    // Menu validation must finish before even profile lookup or Auth provisioning starts.

    @ParameterizedTest
    @ValueSource(strings = {"restaurant.demo@example.com", "RESTAURANT.DEMO@EXAMPLE.COM", "Restaurant.Demo@Example.Com"})
    void reusesExistingAccountsAndSeedsAllThreeRestaurants(String restaurantEmail) throws Exception {
        var json = JsonMapper.builder().build();
        var profilesResource = new ClassPathResource("demo/fixtures/multiple-profiles.json");
        byte[] profiles;
        try (var input = profilesResource.getInputStream()) {
            var accounts = json.readTree(input).asArray();
            accounts.get(1).asObject().put("email", restaurantEmail);
            profiles = json.writeValueAsBytes(accounts);
        }
        var fixtures = new DemoFixtureLoader(json).load(
                new ByteArrayResource(profiles),
                new ClassPathResource("demo/fixtures/multiple-menu.json"));
        var loader = mock(DemoFixtureLoader.class);
        when(loader.load()).thenReturn(fixtures);
        var ids = new HashMap<String, UUID>();
        var roles = new HashMap<UUID, String>();
        for (var account : fixtures.accounts()) {
            String email = account.email().toLowerCase(Locale.ROOT);
            UUID id = UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8));
            ids.put(email, id);
            roles.put(id, account.role().name());
        }
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    UUID id = ids.get(invocation.getArgument(2, String.class));
                    return id == null ? List.of() : List.of(id);
                });
        doThrow(new AssertionError("Existing Auth accounts must be reused")).when(auth).create(any());
        // Simulate lowercase stored emails with exact matching; a missed lookup must not trigger duplicate provisioning.
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenAnswer(invocation -> roles.get(invocation.getArgument(2, UUID.class)));
        var seeder = seeder(loader);

        seeder.run();

        for (var account : fixtures.accounts()) {
            if (account.role() == Role.RESTAURANT) {
                verify(jdbc).update(contains("INSERT INTO public.\"Restaurant\""),
                        eq(ids.get(account.email())), eq(account.restaurantName()), eq(account.status()),
                        eq(account.latitude()), eq(account.longitude()));
            }
        }
        verifyNoInteractions(auth);
        verify(manager).commit(any());
    }
    // Repeated RESTAURANT roles reach their own inserts without recreating existing Auth identities.
}
