package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mosbyboys.dishesbecrazy.application.prc.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.domain.ord.state.AcceptedState;
import com.mosbyboys.dishesbecrazy.domain.ord.state.IllegalTransitionException;
import com.mosbyboys.dishesbecrazy.domain.ord.state.PlacedState;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.Operator;

class ORDFacadeTest {

    private OrderService orderService;
    private OrderEventService orderEventService;
    private MenuService menuService;
    private RestaurantService restaurantService;
    private PricingService pricingService;
    private REGFacade regFacade;
    private OrderQueues queues;
    private ORDFacade facade;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID menuItemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        orderEventService = mock(OrderEventService.class);
        menuService = mock(MenuService.class);
        restaurantService = mock(RestaurantService.class);
        regFacade = mock(REGFacade.class);
        queues = new OrderQueues();
        // same order as the fields in ORDFacade
        facade = new ORDFacade(orderService, orderEventService, new PermissionPolicy(),
                queues, menuService, restaurantService, pricingService, regFacade);
        // The facade looks the caller up through REGFacade, so tell the mock who each id is.
        when(regFacade.getCurrentUser(customerId)).thenReturn(customer());
        when(regFacade.getCurrentUser(restaurantId)).thenReturn(owner(restaurantId));
    }

    // helpers

    private Customer customer() {
        return new Customer(customerId, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                "123 Street", new Location(45.5, -73.6));
    }

    private Restaurant owner(UUID forRestaurant) {
        return new Restaurant(forRestaurant, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                "Resto", RestaurantStatus.OPEN, null);
    }
    
    private Operator operator() {
        return new Operator(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash");
    }

    private OrderLine line() {
        return new OrderLine(menuItemId, 1, new BigDecimal("10.00"));
    }

    private void restaurantIsOpen(boolean open) {
        Restaurant restaurant = mock(Restaurant.class);
        when(restaurant.isOpen()).thenReturn(open);
        when(restaurantService.load(restaurantId)).thenReturn(restaurant);
    }

    private void menuHasAvailableItem() {
        MenuItem item = mock(MenuItem.class);
        when(item.isAvailable()).thenReturn(true);
        when(item.getPrice()).thenReturn(new BigDecimal("10.00"));
        when(item.getName()).thenReturn("Pizza");
        when(menuService.load(menuItemId)).thenReturn(item);
    }

    // a stored order the facade will "load"
    private Order storedOrder() {
        Order order = new Order(customerId, restaurantId, List.of(line()));
        when(orderService.load(order.getId())).thenReturn(order);
        return order;
    }

    // an order that is accepted and waiting in the preparation queue
    private Order acceptedAndQueued() {
        Order order = storedOrder();
        order.accept();
        queues.preparation(restaurantId).enqueue(order.getId());
        return order;
    }

    // placeOrder

    @Test
    void placingAnOrderUsesTheCustomerIdAndJoinsThePlacedQueue() {
        restaurantIsOpen(true);
        menuHasAvailableItem();

        Order order = facade.placeOrder(customer(), restaurantId, List.of(line()));

        assertEquals(customerId, order.getCustomerId());
        assertTrue(queues.placed(restaurantId).contains(order.getId()));
        verify(orderService).save(order);
        verify(orderEventService).save(eq(order.getId()), isNull(), eq("PlacedState"), any(), eq("PLACE"));
    }

    @Test
    void pausedRestaurantRejectsTheOrderAndNothingIsSaved() {
        restaurantIsOpen(false);
        menuHasAvailableItem();

        assertThrows(IllegalArgumentException.class,
                () -> facade.placeOrder(customer(), restaurantId, List.of(line())));

        verify(orderService, never()).save(any());
        assertEquals(0, queues.placed(restaurantId).size());
    }

    @Test
    void aRestaurantCannotPlaceAnOrder() {
        assertThrows(PermissionDeniedException.class,
                () -> facade.placeOrder(owner(restaurantId), restaurantId, List.of(line())));

        verify(orderService, never()).save(any());
    }

    // accept/reject

    @Test
    void acceptingMovesTheOrderFromPlacedToPreparation() {
        Order order = storedOrder();
        queues.placed(restaurantId).enqueue(order.getId());

        facade.acceptOrder(restaurantId, order.getId());

        assertInstanceOf(AcceptedState.class, order.getState());
        assertFalse(queues.placed(restaurantId).contains(order.getId()));
        assertTrue(queues.preparation(restaurantId).contains(order.getId()));
        verify(orderEventService).save(eq(order.getId()), eq("PlacedState"), eq("AcceptedState"), any(), eq("ACCEPT"));
    }

    @Test
    void anOrderAcceptedOutOfTurnIsRefusedAndNothingChanges() {
        Order first = storedOrder();
        Order second = storedOrder();
        queues.placed(restaurantId).enqueue(first.getId());
        queues.placed(restaurantId).enqueue(second.getId());

        assertThrows(IllegalStateException.class,
                () -> facade.acceptOrder(restaurantId, second.getId()));

        assertEquals(first.getId(), queues.placed(restaurantId).peek()); // front unchanged
        assertEquals(2, queues.placed(restaurantId).size()); // nothing removed
        assertInstanceOf(PlacedState.class, second.getState()); // state unchanged
        assertFalse(queues.preparation(restaurantId).contains(second.getId()));
        verify(orderService, never()).save(any());
    }

    @Test
    void anyIncomingOrderCanBeRejected() {
        Order first = storedOrder();
        Order second = storedOrder();
        queues.placed(restaurantId).enqueue(first.getId());
        queues.placed(restaurantId).enqueue(second.getId());

        facade.rejectOrder(restaurantId, second.getId(), "Out of ingredients");

        assertEquals(first.getId(), queues.placed(restaurantId).peek());
        assertFalse(queues.placed(restaurantId).contains(second.getId()));
    }

    @Test
    void anotherRestaurantCannotAcceptAndTheQueuesAreUntouched() {
        Order order = storedOrder();
        queues.placed(restaurantId).enqueue(order.getId());
        UUID otherRestaurant = UUID.randomUUID();
        when(regFacade.getCurrentUser(otherRestaurant)).thenReturn(owner(otherRestaurant));

        assertThrows(PermissionDeniedException.class,
                () -> facade.acceptOrder(otherRestaurant, order.getId()));

        assertInstanceOf(PlacedState.class, order.getState());
        assertTrue(queues.placed(restaurantId).contains(order.getId()));
        assertFalse(queues.preparation(restaurantId).contains(order.getId()));
        verify(orderService, never()).save(any());
    }

    @Test
    void rejectingRemovesTheOrderFromThePlacedQueue() {
        Order order = storedOrder();
        queues.placed(restaurantId).enqueue(order.getId());

        facade.rejectOrder(restaurantId, order.getId(), "Out of ingredients");

        assertFalse(queues.placed(restaurantId).contains(order.getId()));
        assertFalse(queues.preparation(restaurantId).contains(order.getId()));
    }

    // prepare

    @Test
    void theFirstOrderInLineCanStartAndLeavesTheQueue() {
        Order first = acceptedAndQueued();
        Order second = acceptedAndQueued();

        facade.prepareOrder(restaurantId, first.getId());

        assertFalse(queues.preparation(restaurantId).contains(first.getId()));
        assertEquals(second.getId(), queues.preparation(restaurantId).peek());
    }

    @Test
    void anOrderOutOfTurnIsRejectedAndNothingChanges() {
        Order first = acceptedAndQueued();
        Order second = acceptedAndQueued();

        assertThrows(IllegalStateException.class,
                () -> facade.prepareOrder(restaurantId, second.getId()));

        assertEquals(first.getId(), queues.preparation(restaurantId).peek()); // front unchanged
        assertEquals(2, queues.preparation(restaurantId).size()); // nothing removed
        assertInstanceOf(AcceptedState.class, second.getState()); // state unchanged
        verify(orderService, never()).save(any());
    }

    // ready 

    @Test
    void markingReadyJoinsTheReadyQueue() {
        Order order = acceptedAndQueued();
        facade.prepareOrder(restaurantId, order.getId());

        facade.markOrderReady(restaurantId, order.getId());

        assertTrue(queues.ready(restaurantId).contains(order.getId()));
    }

    @Test
    void anIllegalTransitionLeavesTheQueuesAsTheyWere() {
        Order order = storedOrder(); // still placed, so it can't become ready
        queues.placed(restaurantId).enqueue(order.getId());

        assertThrows(IllegalTransitionException.class,
                () -> facade.markOrderReady(restaurantId, order.getId()));

        assertTrue(queues.placed(restaurantId).contains(order.getId()));
        assertFalse(queues.ready(restaurantId).contains(order.getId()));
    }

    // restaurant orders

    @Test
    void aRestaurantSeesItsOrdersWithItemNamesAndWhichIsNextToPrepare() {
        Order first = acceptedAndQueued();
        Order second = acceptedAndQueued();
        when(orderService.findForRestaurant(eq(restaurantId), any())).thenReturn(List.of(first, second));
        when(menuService.getNames(any())).thenReturn(Map.of(menuItemId, "Pizza"));

        List<RestaurantOrderView> views = facade.getRestaurantOrders(owner(restaurantId));

        assertEquals(2, views.size());
        assertEquals("Pizza", views.get(0).itemNames().get(menuItemId));
        assertTrue(views.get(0).nextToPrepare());
        assertFalse(views.get(1).nextToPrepare());
    }

    @Test
    void onlyTheFirstPlacedOrderIsMarkedNextToAccept() {
        Order first = storedOrder();
        Order second = storedOrder();
        queues.placed(restaurantId).enqueue(first.getId());
        queues.placed(restaurantId).enqueue(second.getId());
        when(orderService.findForRestaurant(eq(restaurantId), any())).thenReturn(List.of(first, second));
        when(menuService.getNames(any())).thenReturn(Map.of(menuItemId, "Pizza"));

        List<RestaurantOrderView> views = facade.getRestaurantOrders(owner(restaurantId));

        assertTrue(views.get(0).nextToAccept());
        assertFalse(views.get(1).nextToAccept());
    }

    @Test
    void aCustomerCannotViewARestaurantsOrders() {
        assertThrows(PermissionDeniedException.class, () -> facade.getRestaurantOrders(customer()));

        verify(orderService, never()).findForRestaurant(any(), any());
    }

    // cancel

    @Test
    void cancellingRemovesTheOrderFromWhicheverQueueHoldsIt() {
        Order order = acceptedAndQueued(); // waiting in preparation
        Operator operator = operator();
        when(regFacade.getCurrentUser(operator.getUserId())).thenReturn(operator);

        facade.cancelOrder(operator.getUserId(), order.getId());

        assertFalse(queues.preparation(restaurantId).contains(order.getId()));
    }
}