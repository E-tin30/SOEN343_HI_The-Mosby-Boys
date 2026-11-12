package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import com.mosbyboys.dishesbecrazy.application.prc.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

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
import com.mosbyboys.dishesbecrazy.domain.ord.state.RefundedState;
import com.mosbyboys.dishesbecrazy.domain.reg.Courier;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.Operator;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderObserver;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;

class ORDFacadeTest {

    private OrderService orderService;
    private OrderObserver subscriber;
    private MenuService menuService;
    private RestaurantService restaurantService;
    private PricingService pricingService;
    private REGFacade regFacade;
    private OrderQueues queues;
    private ORDFacade facade;
    private ApplicationEventPublisher eventPublisher;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID menuItemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        subscriber = mock(OrderObserver.class);
        menuService = mock(MenuService.class);
        restaurantService = mock(RestaurantService.class);
        pricingService = mock(PricingService.class);
        // Supplies the existing pricing dependency so history tests can run alongside placement guards.
        regFacade = mock(REGFacade.class);
        pricingService = mock(PricingService.class);
        queues = new OrderQueues();
        eventPublisher = mock(ApplicationEventPublisher.class);
        // same order as the fields in ORDFacade
        facade = new ORDFacade(orderService, new PermissionPolicy(),
                queues, menuService, restaurantService, pricingService, regFacade, eventPublisher);
        facade.subscribe(subscriber);
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

    // a restaurant in the given status, for the pause/open tests
    private Restaurant restaurantWithStatus(RestaurantStatus status) {
        return new Restaurant(restaurantId, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                "Resto", status, null);
    }

    
    private Operator operator() {
        return new Operator(UUID.randomUUID(), OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash");
    }

    private OrderLine line() {
        return new OrderLine(menuItemId, 1, new BigDecimal("10.00"), null);
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
        Order order = new Order(customerId, restaurantId, List.of(line()), new BigDecimal("10.00"));
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
        verify(subscriber).update(new OrderStateChanged(order.getId(), null, "PlacedState", customerId, "PLACE"));
    }

    @ParameterizedTest
    @EnumSource(value = RestaurantStatus.class, names = {"PAUSED", "CLOSED"})
    void unavailableRestaurantRejectsTheOrderAndNothingIsSaved(RestaurantStatus status) {
        Restaurant restaurant = new Restaurant(restaurantId, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                "Resto", status, null);
        when(restaurantService.load(restaurantId)).thenReturn(restaurant);

        assertThrows(IllegalArgumentException.class,
                () -> facade.placeOrder(customer(), restaurantId, List.of(line())));

        verifyNoInteractions(orderService, subscriber);
        assertEquals(0, queues.placed(restaurantId).size());
    }
    // Covers PAUSED and CLOSED restaurants and verifies rejection creates no order, event, or queue entry.

    @Test
    void soldOutItemRejectsTheOrderWithoutSavingOrRecordingAnEvent() {
        restaurantIsOpen(true);
        MenuItem item = mock(MenuItem.class);
        when(item.isAvailable()).thenReturn(false);
        when(item.getName()).thenReturn("Soup");
        when(menuService.load(menuItemId)).thenReturn(item);

        assertThrows(IllegalArgumentException.class,
                () -> facade.placeOrder(customer(), restaurantId, List.of(line())));

        verifyNoInteractions(orderService, subscriber);
        assertEquals(0, queues.placed(restaurantId).size());
    }
    // Confirms the existing sold-out guard rejects placement without persistence or queue side effects.

    // a real item (not a mock) so the stock arithmetic is actually exercised
    private MenuItem realItem(int stock) {
        MenuItem item = new MenuItem(menuItemId, restaurantId, "Pizza", new BigDecimal("10.00"), stock,
                MenuItemStatus.AVAILABLE, "Cheese", null, Instant.now());
        when(menuService.load(menuItemId)).thenReturn(item);
        return item;
    }

    @Test
    void placingAnOrderDecreasesTheItemsStockByTheOrderedQuantity() {
        restaurantIsOpen(true);
        MenuItem item = realItem(5);

        facade.placeOrder(customer(), restaurantId,
                List.of(new OrderLine(menuItemId, 2, new BigDecimal("10.00"), null)));

        assertEquals(3, item.getStockQuantity());
        assertEquals(MenuItemStatus.AVAILABLE, item.getStatus());
        verify(menuService).save(item);
    }

    @Test
    void orderingTheLastUnitsSellsTheItemOut() {
        restaurantIsOpen(true);
        MenuItem item = realItem(2);

        facade.placeOrder(customer(), restaurantId,
                List.of(new OrderLine(menuItemId, 2, new BigDecimal("10.00"), null)));

        assertEquals(0, item.getStockQuantity());
        assertEquals(MenuItemStatus.SOLD_OUT, item.getStatus());
    }

    @Test
    void orderingMoreThanTheStockIsRefusedAndNothingIsSavedOrDeducted() {
        restaurantIsOpen(true);
        MenuItem item = realItem(1);

        assertThrows(IllegalStateException.class, () -> facade.placeOrder(customer(), restaurantId,
                List.of(new OrderLine(menuItemId, 3, new BigDecimal("10.00"), null))));

        assertEquals(1, item.getStockQuantity());
        verify(menuService, never()).save(any());
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
        verify(subscriber).update(new OrderStateChanged(order.getId(), "PlacedState", "AcceptedState", restaurantId, "ACCEPT"));
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
        menuHasAvailableItem();
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
        menuHasAvailableItem();
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
        menuHasAvailableItem();
        Order order = acceptedAndQueued(); // waiting in preparation
        Operator operator = operator();
        when(regFacade.getCurrentUser(operator.getUserId())).thenReturn(operator);

        facade.cancelOrder(operator.getUserId(), order.getId());

        assertFalse(queues.preparation(restaurantId).contains(order.getId()));
    }

    @Test
    void customerHistoryOnlyAsksForTheirOwnOrders() {
        when(orderService.findHistoryForCustomer(customerId)).thenReturn(List.of());

        facade.getOrderHistory(customer());

        verify(orderService).findHistoryForCustomer(customerId);
        verify(orderService, never()).findHistoryForAll();
        verify(orderService, never()).findHistoryForRestaurant(any());
    }

    @Test
    void courierHistoryOnlyAsksForTheirOwnCarriedOrders() {
        UUID courierId = UUID.randomUUID();
        Courier courier = new Courier(courierId, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                CourierState.OFFLINE, new Location(45.5, -73.6));
        when(orderService.findHistoryForCourier(courierId)).thenReturn(List.of());

        facade.getOrderHistory(courier);

        verify(orderService).findHistoryForCourier(courierId);
        verify(orderService, never()).findHistoryForAll();
    }

    @Test
    void operatorRefundSavesRefundedStateAndRecordsTheMatchingEvent() {
        Order order = storedOrder();
        order.accept();
        order.startPreparing();
        order.markReady();
        order.pickUp();
        order.deliver();
        Operator operator = operator();
        when(regFacade.getCurrentUser(operator.getUserId())).thenReturn(operator);

        facade.refundOrder(operator.getUserId(), order.getId());

        assertInstanceOf(RefundedState.class, order.getState());
        verify(orderService).save(order);
        verify(subscriber).update(new OrderStateChanged(order.getId(), "DeliveredState", "RefundedState", operator.getUserId(), "REFUND"));
        assertEquals(0, queues.ready(restaurantId).size());
    }
    // Exercise the real permission policy and domain transition; stored state and refund history must agree.

    @Test
    void customerHistoryNeverLoadsCustomerNames() {
        Order order = new Order(customerId, restaurantId, List.of(line()), new BigDecimal("10.00"));
        when(orderService.findHistoryForCustomer(customerId)).thenReturn(List.of(order));
        when(restaurantService.getNames(any())).thenReturn(Map.of(restaurantId, "Resto"));

        List<OrderHistoryView> views = facade.getOrderHistory(customer());

        assertEquals("Resto", views.get(0).restaurantNames().get(restaurantId));
        assertTrue(views.get(0).customerNames().isEmpty());
        verify(regFacade, never()).getDisplayNames(any());
    }

    @Test
    void operatorHistoryIncludesCustomerNames() {
        Order order = new Order(customerId, restaurantId, List.of(line()), new BigDecimal("10.00"));
        when(orderService.findHistoryForAll()).thenReturn(List.of(order));
        when(regFacade.getDisplayNames(any())).thenReturn(Map.of(customerId, "A B"));

        List<OrderHistoryView> views = facade.getOrderHistory(operator());

        assertEquals("A B", views.get(0).customerNames().get(customerId));
    }

    @Test
    void restaurantHistoryIncludesCustomerNames() {
        Order order = new Order(customerId, restaurantId, List.of(line()), new BigDecimal("10.00"));
        when(orderService.findHistoryForRestaurant(restaurantId)).thenReturn(List.of(order));
        when(regFacade.getDisplayNames(any())).thenReturn(Map.of(customerId, "A B"));

        List<OrderHistoryView> views = facade.getOrderHistory(owner(restaurantId));

        assertEquals("A B", views.get(0).customerNames().get(customerId));
    }

    @Test
    void everySubscriberIsNotifiedOfATransition() {
        OrderObserver second = mock(OrderObserver.class);
        facade.subscribe(second);
        Order order = storedOrder();
        queues.placed(restaurantId).enqueue(order.getId());

        facade.acceptOrder(restaurantId, order.getId());

        verify(subscriber).update(any(OrderStateChanged.class));
        verify(second).update(any(OrderStateChanged.class));
    }

    @Test
    void anUnsubscribedObserverIsNoLongerNotified() {
        facade.unsubscribe(subscriber);
        Order order = storedOrder();
        queues.placed(restaurantId).enqueue(order.getId());

        facade.acceptOrder(restaurantId, order.getId());

        verifyNoInteractions(subscriber);
    }
    // pause / open restaurant

    // an operator pauses an OPEN restaurant: the restaurant that gets saved is PAUSED
    @Test
    void operatorPausesOpenRestaurant() {
        Operator op = operator();
        when(regFacade.getCurrentUser(op.getUserId())).thenReturn(op);
        Restaurant restaurant = restaurantWithStatus(RestaurantStatus.OPEN);
        when(restaurantService.load(restaurantId)).thenReturn(restaurant);

        Restaurant result = facade.pauseRestaurant(op.getUserId(), restaurantId);

        // Assert: it was paused and saved
        assertEquals(RestaurantStatus.PAUSED, result.getStatus());
        verify(restaurantService).save(restaurant);
    }


    // a customer tries to pause: permission denied, and nothing is saved
    @Test
    void customerCannotPauseRestaurant() {
        Customer customer = customer();
        Restaurant restaurant = restaurantWithStatus(RestaurantStatus.OPEN);
        when(restaurantService.load(restaurantId)).thenReturn(restaurant);

        assertThrows(PermissionDeniedException.class, () -> {
            facade.pauseRestaurant(customer.getUserId(), restaurantId);
        });
        verify(restaurantService, never()).save(any());
    }

    // an operator pauses a CLOSED restaurant: refused by Restaurant, and nothing is saved
    @Test
    void operatorCannotPauseClosedRestaurant() {
        Operator op = operator();
        when(regFacade.getCurrentUser(op.getUserId())).thenReturn(op);
        Restaurant restaurant = restaurantWithStatus(RestaurantStatus.CLOSED);
        when(restaurantService.load(restaurantId)).thenReturn(restaurant);

        assertThrows(IllegalStateException.class, () -> {
            facade.pauseRestaurant(op.getUserId(), restaurantId);
        });
        verify(restaurantService, never()).save(any());
    }

    // a restaurant opens itself from PAUSED: the restaurant that gets saved is OPEN
    @Test
    void ownerOpensOwnPausedRestaurant() {
        Restaurant owner = owner(restaurantId);
        when(regFacade.getCurrentUser(owner.getUserId())).thenReturn(owner);
        Restaurant restaurant = restaurantWithStatus(RestaurantStatus.PAUSED);
        when(restaurantService.load(restaurantId)).thenReturn(restaurant);

        Restaurant result = facade.openRestaurant(owner.getUserId(), restaurantId);

        assertEquals(RestaurantStatus.OPEN, result.getStatus());
        verify(restaurantService).save(restaurant);
    }

    // an operator opens a PAUSED restaurant: the restaurant that gets saved is OPEN
    @Test
    void operatorOpensPausedRestaurant() {
        Operator op = operator();
        when(regFacade.getCurrentUser(op.getUserId())).thenReturn(op);
        Restaurant restaurant = restaurantWithStatus(RestaurantStatus.PAUSED);
        when(restaurantService.load(restaurantId)).thenReturn(restaurant);

        Restaurant result = facade.openRestaurant(op.getUserId(), restaurantId);

        // Assert: it was opened and saved
        assertEquals(RestaurantStatus.OPEN, result.getStatus());
        verify(restaurantService).save(restaurant);
    }

    @Test
    void rejectingAnOrderGivesItsStockBack() {
        MenuItem item = realItem(3);
        Order order = storedOrder(); // one unit of the item
        queues.placed(restaurantId).enqueue(order.getId());

        facade.rejectOrder(restaurantId, order.getId(), "Out of ingredients");

        assertEquals(4, item.getStockQuantity());
        verify(menuService).save(item);
    }

    @Test
    void cancellingBeforePickupGivesTheStockBack() {
        MenuItem item = realItem(3);
        Order order = acceptedAndQueued();
        Operator operator = operator();
        when(regFacade.getCurrentUser(operator.getUserId())).thenReturn(operator);

        facade.cancelOrder(operator.getUserId(), order.getId());

        assertEquals(4, item.getStockQuantity());
    }

    @Test
    void cancellingAfterPickupDoesNotGiveStockBack() {
        MenuItem item = realItem(3);
        Order order = storedOrder();
        order.accept();
        order.startPreparing();
        order.markReady();
        order.pickUp();
        Operator operator = operator();
        when(regFacade.getCurrentUser(operator.getUserId())).thenReturn(operator);

        facade.cancelOrder(operator.getUserId(), order.getId());

        assertEquals(3, item.getStockQuantity());
        verify(menuService, never()).save(any());
    }

    @Test
    void givingStockBackReopensAnItemThatSoldOut() {
        MenuItem item = realItem(1);
        item.decreaseStock(1); // sold out
        Order order = storedOrder();
        queues.placed(restaurantId).enqueue(order.getId());

        facade.rejectOrder(restaurantId, order.getId(), "Out of ingredients");

        assertEquals(1, item.getStockQuantity());
        assertEquals(MenuItemStatus.AVAILABLE, item.getStatus());
    }
}
