package com.mosbyboys.dishesbecrazy.technical.seed;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

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
            var scope = scope(baseline, users);
            var sql = new NamedParameterJdbcTemplate(jdbc);
            checkOwnership(sql, scope);
            clearDemoActivity(sql, scope);
            catalogue.restore(baseline, users);
            for (var order : baseline.orders()) {
                orders.seedMissing(order, users);
            }
        });
    }
    // The future Reset coordinator calls this database step while demo commands are stopped, then rebuilds runtime state.

    private MapSqlParameterSource scope(DemoFixtures baseline, Map<String, UUID> users) {
        return new MapSqlParameterSource()
                .addValue("customers", baseline.accounts().stream().filter(account -> account.role() == Role.CUSTOMER)
                        .map(account -> users.get(account.username())).toList())
                .addValue("restaurants", baseline.accounts().stream().filter(account -> account.role() == Role.RESTAURANT)
                        .map(account -> users.get(account.username())).toList())
                .addValue("menuItems", baseline.menuItems().stream().map(DemoFixtures.MenuSeed::id).toList())
                .addValue("fixtureOrders", baseline.orders().stream().map(DemoFixtures.OrderSeed::id).toList());
    }
    // An order belongs to the demo when its customer OR restaurant is a fixture account, including orders placed elsewhere.

    private void checkOwnership(NamedParameterJdbcTemplate sql, MapSqlParameterSource scope) {
        int conflicts = sql.queryForObject("""
                SELECT COUNT(*) FROM public."MenuItem"
                WHERE id IN (:menuItems) AND restaurant_id NOT IN (:restaurants)
                """, scope, Integer.class);
        Assert.state(conflicts == 0, "A fixture menu ID belongs to a restaurant outside the demo.");
        conflicts = sql.queryForObject("""
                SELECT COUNT(*) FROM public."Order" WHERE id IN (:fixtureOrders)
                    AND user_id NOT IN (:customers) AND restaurant_id NOT IN (:restaurants)
                """, scope, Integer.class);
        Assert.state(conflicts == 0, "A fixture order ID belongs to an order outside the demo.");
        conflicts = sql.queryForObject("""
                SELECT COUNT(*) FROM public."OrderLineItem" line
                JOIN public."Order" o ON o.id = line.order_id
                JOIN public."MenuItem" menu ON menu.id = line.menu_item_id
                WHERE menu.restaurant_id IN (:restaurants) AND menu.id NOT IN (:menuItems)
                    AND o.user_id NOT IN (:customers) AND o.restaurant_id NOT IN (:restaurants)
                """, scope, Integer.class);
        Assert.state(conflicts == 0, "An order outside the demo references an extra demo menu item; resolve it before restoration.");
    }
    // Refuse ownership collisions or cross-demo cascades rather than silently deleting another team's order lines.

    private void clearDemoActivity(NamedParameterJdbcTemplate sql, MapSqlParameterSource scope) {
        var ids = sql.queryForList("""
                SELECT id FROM public."Order" WHERE user_id IN (:customers) OR restaurant_id IN (:restaurants)
                """, scope, UUID.class);
        sql.update("""
                DELETE FROM public."Bill" WHERE user_id IN (:customers) OR restaurant_id IN (:restaurants)
                """, scope);
        if (!ids.isEmpty()) {
            scope.addValue("orders", ids);
            sql.update("DELETE FROM public.\"Bill\" WHERE order_id IN (:orders)", scope);
            sql.update("DELETE FROM public.\"Event\" WHERE order_id IN (:orders)", scope);
            sql.update("DELETE FROM public.\"OrderLineItem\" WHERE order_id IN (:orders)", scope);
            sql.update("DELETE FROM public.\"Order\" WHERE id IN (:orders)", scope);
        }
        sql.update("""
                DELETE FROM public."MenuItem" WHERE restaurant_id IN (:restaurants) AND id NOT IN (:menuItems)
                """, scope);
    }
    // Remove current demo history and dependent bills before menus; all failures roll back the complete database step.
}
