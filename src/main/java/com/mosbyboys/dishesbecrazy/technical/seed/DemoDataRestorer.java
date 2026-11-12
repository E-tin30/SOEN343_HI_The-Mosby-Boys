package com.mosbyboys.dishesbecrazy.technical.seed;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.mosbyboys.dishesbecrazy.domain.reg.Role;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DemoDataRestorer {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DemoFixtureLoader fixtures;
    private final DemoCatalogueRestorer catalogue;
    private final DemoOrderStore orders;

    public void restore() throws IOException {
        DemoFixtures baseline = fixtures.load();
        transaction.executeWithoutResult(status -> {
            var users = catalogue.resolveUsers(baseline);
            clearActivity();
            catalogue.restore(baseline, users);
            removeExtraCatalogue(baseline, users);
            for (var order : baseline.orders()) {
                orders.seedMissing(order, users);
            }
        });
    }
    // Restore one global starting point for every walkthrough; resolve Auth identities before any database writes.

    private void clearActivity() {
        jdbc.update("DELETE FROM public.\"Bill\"");
        jdbc.update("DELETE FROM public.\"Event\"");
        jdbc.update("DELETE FROM public.\"OrderLineItem\"");
        jdbc.update("DELETE FROM public.\"Order\"");
    }
    // Clear everyone's activity in foreign-key order; the transaction also rolls these deletes back if reinserting the baseline fails.

    private void removeExtraCatalogue(DemoFixtures baseline, Map<String, UUID> users) {
        var scope = new MapSqlParameterSource()
                .addValue("users", users.values())
                .addValue("customers", baseline.accounts().stream().filter(account -> account.role() == Role.CUSTOMER)
                        .map(account -> users.get(account.username())).toList())
                .addValue("restaurants", baseline.accounts().stream().filter(account -> account.role() == Role.RESTAURANT)
                        .map(account -> users.get(account.username())).toList())
                .addValue("couriers", baseline.accounts().stream().filter(account -> account.role() == Role.COURIER)
                        .map(account -> users.get(account.username())).toList())
                .addValue("menuItems", baseline.menuItems().stream().map(DemoFixtures.MenuSeed::id).toList());
        var sql = new NamedParameterJdbcTemplate(jdbc);
        sql.update("DELETE FROM public.\"MenuItem\" WHERE id NOT IN (:menuItems)", scope);
        sql.update("DELETE FROM public.\"Customer\" WHERE user_id NOT IN (:customers)", scope);
        sql.update("DELETE FROM public.\"Courier\" WHERE user_id NOT IN (:couriers)", scope);
        sql.update("DELETE FROM public.\"Restaurant\" WHERE user_id NOT IN (:restaurants)", scope);
        sql.update("DELETE FROM public.\"User\" WHERE id NOT IN (:users)", scope);
    }
    // Repair fixture ownership first, then remove every non-fixture profile/menu and any stale role-specific rows.
    // Auth identities are separate from application data and are never deleted by Reset.
}
