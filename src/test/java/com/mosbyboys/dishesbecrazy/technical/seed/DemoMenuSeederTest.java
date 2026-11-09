package com.mosbyboys.dishesbecrazy.technical.seed;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.mosbyboys.dishesbecrazy.domain.reg.Role;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DemoMenuSeederTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);

    @Test
    void invalidProfilesPreventMenuLookupsAndWrites() throws Exception {
        var loader = spy(new DemoFixtureLoader(JsonMapper.builder().build()));
        doAnswer(invocation -> loader.load(new ByteArrayResource("[]".getBytes(StandardCharsets.UTF_8)),
                new ClassPathResource("demo/menu.json"))).when(loader).load();
        var seeder = new DemoMenuSeeder(jdbc, new TransactionTemplate(manager), loader);

        assertThrows(IllegalArgumentException.class, seeder::run);

        verifyNoInteractions(jdbc, manager);
    }
    // The menu runner also performs full preflight before opening a transaction.

    @Test
    void invalidOrdersPreventMenuLookupsAndWrites() throws Exception {
        var loader = spy(new DemoFixtureLoader(JsonMapper.builder().build()));
        doAnswer(invocation -> loader.load(new ClassPathResource("demo/profiles.json"),
                new ClassPathResource("demo/menu.json"),
                new ByteArrayResource("[]".getBytes(StandardCharsets.UTF_8)))).when(loader).load();
        var seeder = new DemoMenuSeeder(jdbc, new TransactionTemplate(manager), loader);

        assertThrows(IllegalArgumentException.class, seeder::run);

        verifyNoInteractions(jdbc, manager);
    }
    // A valid catalogue cannot hide invalid orders in the same production baseline.

    @Test
    void insertsEachMenuForItsOwnRestaurantIdentity() throws Exception {
        var fixtures = new DemoFixtureLoader(JsonMapper.builder().build()).load();
        var loader = mock(DemoFixtureLoader.class);
        when(loader.load()).thenReturn(fixtures);
        var ids = new HashMap<String, UUID>();
        for (var account : fixtures.accounts()) {
            if (account.role() == Role.RESTAURANT) {
                ids.put(account.username(), UUID.nameUUIDFromBytes(account.username().getBytes(StandardCharsets.UTF_8)));
            }
        }
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(Object[].class)))
                .thenAnswer(invocation -> List.of(ids.get(invocation.getArgument(2, String.class))));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var seeder = new DemoMenuSeeder(jdbc, new TransactionTemplate(manager), loader);

        seeder.run();

        for (var item : fixtures.menuItems()) {
            verify(jdbc).update(contains("INSERT INTO public.\"MenuItem\""),
                    eq(item.id()), eq(ids.get(item.restaurantUsername())), eq(item.name()), eq(item.price()),
                    eq(item.stockQuantity()), eq(item.status().name()), eq(item.description()));
        }
        verify(manager).commit(any());
    }
    // All twelve production menu items retain ownership without contacting the shared database.
}
