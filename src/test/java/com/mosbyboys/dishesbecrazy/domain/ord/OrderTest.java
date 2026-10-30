package com.mosbyboys.dishesbecrazy.domain.ord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.domain.ord.state.AcceptedState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.IllegalTransitionException;
import com.mosbyboys.dishesbecrazy.domain.ord.state.PlacedState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.PreparingState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.ReadyState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.RejectedState;

public class OrderTest {

    // Helper to create valid orders
    private Order newOrder() {
        OrderLine line1 = new OrderLine(UUID.randomUUID(), 3, new BigDecimal("12.50"));
        OrderLine line2 = new OrderLine(UUID.randomUUID(), 2, new BigDecimal("4.25"));
        return new Order(UUID.randomUUID(), UUID.randomUUID(), List.of(line1, line2), new BigDecimal("46.00"));
    }
    
    // Helper methods to get orders at certain states
    private Order acceptedOrder()  { Order o = newOrder(); o.accept(); return o; }
    private Order preparingOrder() { Order o = acceptedOrder(); o.startPreparing(); return o; }
    private Order readyOrder()     { Order o = preparingOrder(); o.markReady(); return o; }
    
    // a new order starts in PlacedState
    @Test
    void newOrderStartsInPlacedState() {
        Order order = newOrder();

        assertInstanceOf(PlacedState.class, order.getState());
    }

    // an accepted order in AcceptedState
    @Test
    void acceptedOrderInAcceptedState() {
        Order acceptedOrder = acceptedOrder();

        assertInstanceOf(AcceptedState.class, acceptedOrder.getState());
    }

    // an preparing order order in PreparingState
    @Test
    void acceptedOrderMovedToStartPreparing() {
        Order preparingOrder = preparingOrder();

        assertInstanceOf(PreparingState.class, preparingOrder.getState());
    }

    // an order marked ready in ReadyState
    @Test
    void readyOrderMovedToReadyState() {
        Order readyOrder = readyOrder();

        assertInstanceOf(ReadyState.class, readyOrder.getState());
    }

    // test order lifecycle
    @Test
    void fullOrderLifecycleWorks() {
        Order order = newOrder();
        order.accept();
        order.startPreparing();
        order.markReady();

        assertInstanceOf(ReadyState.class, order.getState());
    }

    // an order with state accepted can't transition to ready (no skipped transitions)
    @Test
    void acceptedOrderMovedToReadyStateThrowsException() {
        Order acceptedOrder = acceptedOrder();

        assertThrows(IllegalTransitionException.class, () -> acceptedOrder.markReady());
        assertInstanceOf(AcceptedState.class, acceptedOrder.getState());
    }

    // an order with state placed can't transition to StartPreparing (no skipped transitions)
    @Test
    void placedOrderMovedToStartPreparingStateThrowsException() {
        Order placedOrder = newOrder();

        assertThrows(IllegalTransitionException.class, () -> placedOrder.startPreparing());
        assertInstanceOf(PlacedState.class, placedOrder.getState());
    }

    // a rejected order needs a reason
    @Test
    void rejectedOrderNeedsReason() {
        Order placedOrder = newOrder();
        placedOrder.reject("Restaurant closing soon");

        assertInstanceOf(RejectedState.class, placedOrder.getState());
    }

    // a rejected order needs a reason or its throws
    @Test
    void rejectedOrderWithNoReasonThrows() {
        Order placedOrder = newOrder();

        assertThrows(IllegalArgumentException.class, () -> placedOrder.reject(""));
        assertInstanceOf(PlacedState.class, placedOrder.getState());
    }

    // getTotal returns correct total amount
    @Test
    void getTotalReturnsCorrectTotal() {
        Order order = newOrder();
        BigDecimal expectedLineTotal = new BigDecimal("46.00");

           assertEquals(0, expectedLineTotal.compareTo(order.getSubtotal()));
    }

    // creating an order with null line items throws
    @Test
    void orderCreationWithNullLineItemThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Order(UUID.randomUUID(), UUID.randomUUID(), null, null));
    }
}
