package com.mosbyboys.dishesbecrazy.application.prc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderObserver;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;

import lombok.RequiredArgsConstructor;

/* The ledger (R-PRC-03, R-PRC-05), stored in the Bill table: a CHARGE row when an order is delivered
and a separate REFUND row when it is refunded. Rows are only inserted, never updated, so a refund
leaves the original charge unchanged. Fed by order state changes, like OrderEventLogger. */
@Service
@RequiredArgsConstructor
public class LedgerService implements OrderObserver {

    private static final Map<String, String> ENTRY_FOR_STATE =
            Map.of("DeliveredState", "CHARGE", "RefundedState", "REFUND");

    private final OrderService orderService;
    private final PricingService pricingService;
    private final JdbcTemplate jdbc;

    @Override
    public void update(OrderStateChanged event) {
        record(event.orderId(), event.newState(), Instant.now());
    }

    // Adds the entries missing for delivered or refunded orders, e.g. the fixture orders after a Reset
    public void recordMissing() {
        jdbc.queryForList("""
                SELECT order_id, new_state, created_at FROM public."Event"
                WHERE new_state IN ('DeliveredState', 'RefundedState') ORDER BY created_at
                """).forEach(row -> record((UUID) row.get("order_id"), (String) row.get("new_state"),
                        ((Timestamp) row.get("created_at")).toInstant()));
    }

    public List<LedgerEntry> getEntries() {
        return jdbc.query("""
                SELECT b.order_id, b.status, b.total, b.created_at, o.state, o.created_at AS placed_at, r.name
                FROM public."Bill" b
                JOIN public."Order" o ON o.id = b.order_id
                JOIN public."Restaurant" r ON r.user_id = b.restaurant_id
                ORDER BY b.created_at DESC
                """, (rs, i) -> new LedgerEntry(
                        rs.getObject("order_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getBigDecimal("total"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("placed_at").toInstant(),
                        rs.getString("state").replace("State", "").toUpperCase()));
    }

    private void record(UUID orderId, String newState, Instant at) {
        String entry = ENTRY_FOR_STATE.get(newState);
        if (entry == null || hasEntry(orderId, entry)) {
            return;
        }
        Order order = orderService.load(orderId);
        if (order.getSubtotal() == null) {
            return;
        }
        PricingBreakdown price = pricingService.calculateTotal(order.getSubtotal());

        if (entry.equals("CHARGE")) {
            insert(order, entry, at, price.subtotal(), price.total(), price.deliveryFee(), price.serviceFee(),
                    price.smallOrderSurcharge());
        } else {
            // refund exactly what was charged
            BigDecimal charged = jdbc.queryForList("""
                    SELECT total FROM public."Bill" WHERE order_id = ? AND status = 'CHARGE'
                    """, BigDecimal.class, orderId).stream().findFirst().orElse(price.total());
            insert(order, entry, at, null, charged.negate(), null, null, null);
        }
    }

    private boolean hasEntry(UUID orderId, String entry) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public."Bill" WHERE order_id = ? AND status = ?
                """, Integer.class, orderId, entry);
        return count != null && count > 0;
    }

    private void insert(Order order, String entry, Instant at, BigDecimal subtotal, BigDecimal total,
            BigDecimal deliveryFee, BigDecimal serviceFee, BigDecimal surcharge) {
        jdbc.update("""
                INSERT INTO public."Bill" (id, created_at, order_id, user_id, restaurant_id, status,
                    subtotal, total, delivery_fee, service_fee, small_order_surchage)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), Timestamp.from(at), order.getId(), order.getCustomerId(),
                order.getRestaurantId(), entry, subtotal, total, deliveryFee, serviceFee, surcharge);
    }
}
