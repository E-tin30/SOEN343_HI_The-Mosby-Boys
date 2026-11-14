package com.mosbyboys.dishesbecrazy.application.prc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mosbyboys.dishesbecrazy.application.ord.OrderService;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;

class LedgerServiceTest {
    private final UUID orderId = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private LedgerService ledger;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
                CREATE TABLE public."Bill" (id uuid PRIMARY KEY, created_at timestamp, order_id uuid, user_id uuid,
                    restaurant_id uuid, status varchar, subtotal numeric, total numeric, delivery_fee numeric,
                    service_fee numeric, small_order_surchage numeric)
                """);

        var config = new PricingConfiguration();
        config.setDeliveryFee(new BigDecimal("4.99"));
        config.setServiceFeeRate(new BigDecimal("0.12"));
        config.setSmallOrderThreshold(new BigDecimal("15.00"));
        config.setSmallOrderSurcharge(new BigDecimal("2.50"));
        config.setGst(new BigDecimal("0.05"));
        config.setQst(new BigDecimal("0.09975"));

        var order = mock(Order.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getCustomerId()).thenReturn(UUID.randomUUID());
        when(order.getRestaurantId()).thenReturn(UUID.randomUUID());
        when(order.getSubtotal()).thenReturn(new BigDecimal("20.00"));
        var orders = mock(OrderService.class);
        when(orders.load(orderId)).thenReturn(order);

        ledger = new LedgerService(orders, new PricingService(config), jdbc);
    }

    private void transition(String newState) {
        ledger.update(new OrderStateChanged(orderId, "x", newState, UUID.randomUUID(), "x"));
    }

    private List<BigDecimal> totals(String status) {
        return jdbc.queryForList("SELECT total FROM public.\"Bill\" WHERE status = ?", BigDecimal.class, status);
    }

    @Test
    void refundIsASeparateEntryAndLeavesTheChargeUnchanged() {
        transition("DeliveredState");
        BigDecimal charged = totals("CHARGE").getFirst();

        transition("RefundedState");

        assertEquals(List.of(charged), totals("CHARGE"));
        assertEquals(0, charged.negate().compareTo(totals("REFUND").getFirst()));
    }

    @Test
    void recordsEachEntryOnceAndIgnoresOtherStates() {
        transition("ReadyState");
        transition("DeliveredState");
        transition("DeliveredState");

        assertEquals(1, totals("CHARGE").size());
        assertEquals(0, totals("REFUND").size());
    }
}
