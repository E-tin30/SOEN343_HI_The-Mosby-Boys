package com.mosbyboys.dishesbecrazy.application.ntf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;
import com.mosbyboys.dishesbecrazy.domain.ord.state.OrderStates;

class OrderNotifierTest {

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    private ORDFacade ord;
    private NotificationService notifications;
    private OrderNotifier notifier;

    @BeforeEach
    void setUp() {
        ord = mock(ORDFacade.class);
        notifications = mock(NotificationService.class);
        notifier = new OrderNotifier(ord, notifications);

        Order order = Order.reconstitute(orderId, OrderStates.fromName("AcceptedState"), customerId,
                restaurantId, null, List.of(), Instant.now(), null, BigDecimal.TEN);
        when(ord.getOrder(orderId)).thenReturn(order);
        when(ord.getRestaurantNames(Set.of(restaurantId))).thenReturn(Map.of(restaurantId, "Tasty Place"));
    }

    private OrderStateChanged change(String newState) {
        return new OrderStateChanged(orderId, "PlacedState", newState, restaurantId, "ACTION");
    }

    @Test
    void notifiesTheCustomerWithTheRestaurantName() {
        notifier.update(change("AcceptedState"));

        verify(notifications).notify(eq(customerId), eq("AcceptedState"), contains("Tasty Place"), eq(orderId));
    }

    @ParameterizedTest
    @ValueSource(strings = { "PlacedState", "AcceptedState", "RejectedState", "PreparingState", "ReadyState",
            "PickedUpState", "DeliveredState", "CancelledState", "RefundedState" })
    void everyStateChangeNotifiesTheCustomer(String state) {
        notifier.update(change(state));

        verify(notifications).notify(eq(customerId), eq(state), contains("Tasty Place"), eq(orderId));
    }

    @Test
    void fallsBackToAGenericNameWhenTheRestaurantIsUnknown() {
        when(ord.getRestaurantNames(Set.of(restaurantId))).thenReturn(Map.of());

        notifier.update(change("ReadyState"));

        verify(notifications).notify(eq(customerId), eq("ReadyState"), contains("the restaurant"), eq(orderId));
    }

    @Test
    void ignoresAStateItHasNoMessageFor() {
        notifier.update(change("SomeFutureState"));

        verifyNoInteractions(notifications);
    }

    @Test
    void aFailureNeverEscapesTheObserver() {
        when(ord.getOrder(orderId)).thenThrow(new IllegalArgumentException("Order not found"));

        assertDoesNotThrow(() -> notifier.update(change("AcceptedState")));
        verifyNoInteractions(notifications);
    }
}