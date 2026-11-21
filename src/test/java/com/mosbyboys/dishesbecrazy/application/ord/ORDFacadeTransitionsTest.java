package com.mosbyboys.dishesbecrazy.application.ord;

import static com.mosbyboys.dishesbecrazy.application.ord.ORDFacadeTransitionsTest.Transition.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

import com.mosbyboys.dishesbecrazy.application.prc.PricingService;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItem;
import com.mosbyboys.dishesbecrazy.domain.ord.MenuItemStatus;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderObserver;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderStateChanged;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.domain.ord.state.IllegalTransitionException;
import com.mosbyboys.dishesbecrazy.domain.reg.Courier;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.Operator;

// OrderQueues is real, so InOrder can't see it: ordering is checked through the timeline instead.
class ORDFacadeTransitionsTest {

    enum Transition { ACCEPT, REJECT, CANCEL, PREPARE, READY, PICKUP, DELIVER, REFUND }

    private OrderService orderService;
    private OrderObserver subscriber;
    private MenuService menuService;
    private REGFacade regFacade;
    private ApplicationEventPublisher eventPublisher;
    private OrderQueues queues;
    private ORDFacade facade;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID menuItemId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();

    private final List<String> timeline = new ArrayList<>();
    private final List<OrderStateChanged> events = new ArrayList<>();
    private UUID watched;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        subscriber = mock(OrderObserver.class);
        menuService = mock(MenuService.class);
        regFacade = mock(REGFacade.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        queues = new OrderQueues();
        facade = new ORDFacade(orderService, new PermissionPolicy(), queues, menuService,
                mock(RestaurantService.class), mock(PricingService.class), regFacade, eventPublisher);
        facade.subscribe(subscriber);

        when(regFacade.getCurrentUser(customerId)).thenReturn(customer(customerId));
        when(regFacade.getCurrentUser(restaurantId)).thenReturn(owner(restaurantId));
        when(regFacade.getCurrentUser(operatorId)).thenReturn(
                new Operator(operatorId, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash"));

        // Order is mutable: record its state when save is called, not afterwards.
        doAnswer(inv -> {
            Order saved = inv.getArgument(0);
            timeline.add("save:" + saved.getState());
            return null;
        }).when(orderService).save(any());

        doAnswer(inv -> {
            OrderStateChanged event = inv.getArgument(0);
            events.add(event);
            timeline.add("event:" + event.action() + " " + queuesOf(event.orderId()));
            return null;
        }).when(subscriber).update(any());

        doAnswer(inv -> {
            MenuItem item = inv.getArgument(0);
            timeline.add("stock:" + item.getStockQuantity());
            return null;
        }).when(menuService).save(any());

        doAnswer(inv -> {
            timeline.add(inv.getArgument(0).getClass().getSimpleName() + " " + queuesOf(watched));
            return null;
        }).when(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void acceptSavesThenPublishesThenMovesThePlacedOrderToPreparation() {
        Order order = arrangeLegal(ACCEPT);

        facade.acceptOrder(restaurantId, order.getId());

        assertEquals(List.of("save:AcceptedState", "event:ACCEPT [placed]"), timeline);
        assertEquals(List.of(event(order, "PlacedState", "AcceptedState", restaurantId, "ACCEPT")), events);
        assertEquals("[preparation]", queuesOf(order.getId()));
    }

    @Test
    void rejectSavesThenPublishesThenGivesStockBackAndLeavesThePlacedQueue() {
        realItem(3);
        Order order = arrangeLegal(REJECT);

        facade.rejectOrder(restaurantId, order.getId(), "Out of ingredients");

        assertEquals(List.of("save:RejectedState", "event:REJECT [placed]", "stock:4"), timeline);
        assertEquals(List.of(event(order, "PlacedState", "RejectedState", restaurantId, "REJECT")), events);
        assertEquals("[]", queuesOf(order.getId()));
    }

    @Test
    void customerCancelSavesThenPublishesThenGivesStockBackAndLeavesThePlacedQueue() {
        realItem(3);
        Order order = arrangeLegal(CANCEL);

        facade.cancelOrder(customerId, order.getId());

        assertEquals(List.of("save:CancelledState", "event:CANCEL [placed]", "stock:4"), timeline);
        assertEquals(List.of(event(order, "PlacedState", "CancelledState", customerId, "CANCEL")), events);
        assertEquals("[]", queuesOf(order.getId()));
    }

    @Test
    void operatorCancelOfAReadyOrderLeavesBothReadyQueuesAfterTheEvent() {
        realItem(3);
        Order order = orderIn("ReadyState");

        facade.cancelOrder(operatorId, order.getId());

        assertEquals(List.of("save:CancelledState", "event:CANCEL [ready, globalReady]", "stock:4"), timeline);
        assertEquals(List.of(event(order, "ReadyState", "CancelledState", operatorId, "CANCEL")), events);
        assertEquals("[]", queuesOf(order.getId()));
    }

    // CancelledState.foodHasLeftRestaurant() is false, so reading it after cancel() would restock.
    @Test
    void operatorCancelAfterPickupKeepsTheStockBecauseFoodHasLeftIsReadBeforeTheChange() {
        MenuItem item = realItem(3);
        Order order = orderIn("PickedUpState");

        facade.cancelOrder(operatorId, order.getId());

        assertEquals(List.of("save:CancelledState", "event:CANCEL []"), timeline);
        assertEquals(List.of(event(order, "PickedUpState", "CancelledState", operatorId, "CANCEL")), events);
        assertEquals(3, item.getStockQuantity());
    }

    @Test
    void prepareSavesThenPublishesWithActionPreparingThenLeavesThePreparationQueue() {
        Order order = arrangeLegal(PREPARE);

        facade.prepareOrder(restaurantId, order.getId());

        assertEquals(List.of("save:PreparingState", "event:PREPARING [preparation]"), timeline);
        assertEquals(List.of(event(order, "AcceptedState", "PreparingState", restaurantId, "PREPARING")), events);
        assertEquals("[]", queuesOf(order.getId()));
    }

    @Test
    void readySavesThenPublishesThenJoinsBothReadyQueuesThenSendsOrderReadyEvent() {
        Order order = arrangeLegal(READY);

        facade.markOrderReady(restaurantId, order.getId());

        assertEquals(List.of(
                "save:ReadyState",
                "event:READY []",
                "OrderReadyEvent [ready, globalReady]"), timeline);
        assertEquals(List.of(event(order, "PreparingState", "ReadyState", restaurantId, "READY")), events);
        assertEquals("[ready, globalReady]", queuesOf(order.getId()));
    }

    @Test
    void pickUpSavesThenPublishesWithActionPickupAndLeavesTheQueuesAlone() {
        Order order = arrangeLegal(PICKUP);

        facade.pickUpOrder(courierId, order.getId());

        assertEquals(List.of("save:PickedUpState", "event:PICKUP [ready, globalReady]"), timeline);
        assertEquals(List.of(event(order, "ReadyState", "PickedUpState", courierId, "PICKUP")), events);
        assertEquals("[ready, globalReady]", queuesOf(order.getId()));
    }

    @Test
    void deliverSavesThenPublishes() {
        Order order = arrangeLegal(DELIVER);

        facade.deliverOrder(courierId, order.getId());

        assertEquals(List.of("save:DeliveredState", "event:DELIVER []"), timeline);
        assertEquals(List.of(event(order, "PickedUpState", "DeliveredState", courierId, "DELIVER")), events);
    }

    @Test
    void refundSavesThenPublishes() {
        Order order = arrangeLegal(REFUND);

        facade.refundOrder(operatorId, order.getId());

        assertEquals(List.of("save:RefundedState", "event:REFUND []"), timeline);
        assertEquals(List.of(event(order, "DeliveredState", "RefundedState", operatorId, "REFUND")), events);
    }

    @ParameterizedTest
    @EnumSource(Transition.class)
    void aCallerWithoutPermissionChangesNothing(Transition t) {
        MenuItem item = realItem(3);
        Order order = arrangeLegal(t);
        UUID orderId = order.getId();
        String stateBefore = order.getState().toString();
        String queuesBefore = queuesOf(orderId);
        UUID caller = wrongCaller(t);

        assertThrows(PermissionDeniedException.class, () -> run(t, caller, orderId));

        assertNothingChanged(order, stateBefore, queuesBefore, item);
    }

    // checkCanRefund already refuses undelivered orders, so refund never reaches the state machine.
    @ParameterizedTest
    @EnumSource(value = Transition.class, names = "REFUND", mode = EnumSource.Mode.EXCLUDE)
    void anIllegalTransitionChangesNothing(Transition t) {
        MenuItem item = realItem(3);
        Order order = arrangeIllegal(t);
        UUID orderId = order.getId();
        String stateBefore = order.getState().toString();
        String queuesBefore = queuesOf(orderId);

        // a customer fails the permission check outside PlacedState; the operator never does
        UUID caller = t == CANCEL ? operatorId : rightCaller(t);

        assertThrows(IllegalTransitionException.class, () -> run(t, caller, orderId));

        assertNothingChanged(order, stateBefore, queuesBefore, item);
    }

    @Test
    void refundingAnUndeliveredOrderIsRefusedByThePermissionCheckAndChangesNothing() {
        MenuItem item = realItem(3);
        Order order = orderIn("PlacedState");
        UUID orderId = order.getId();

        assertThrows(PermissionDeniedException.class, () -> facade.refundOrder(operatorId, orderId));

        assertNothingChanged(order, "PlacedState", "[placed]", item);
    }

    @ParameterizedTest
    @EnumSource(Transition.class)
    void onlyRejectAndCancelLockTheOrderForUpdate(Transition t) {
        realItem(3);
        Order order = arrangeLegal(t);

        run(t, rightCaller(t), order.getId());

        boolean locks = t == REJECT || t == CANCEL;
        verify(orderService, times(locks ? 1 : 0)).loadForUpdate(order.getId());
        verify(orderService, times(locks ? 0 : 1)).load(order.getId());
    }

    @Test
    void acceptLooksUpTheOrderBeforeTheUser() {
        UUID missingOrder = UUID.randomUUID();
        UUID missingUser = UUID.randomUUID();
        nothingExistsFor(missingOrder, missingUser);

        NoSuchElementException e = assertThrows(NoSuchElementException.class,
                () -> facade.acceptOrder(missingUser, missingOrder));

        assertEquals("order not found", e.getMessage());
        verify(regFacade, never()).getCurrentUser(missingUser);
    }

    @ParameterizedTest
    @EnumSource(value = Transition.class, names = "ACCEPT", mode = EnumSource.Mode.EXCLUDE)
    void theOtherTransitionsLookUpTheUserBeforeTheOrder(Transition t) {
        UUID missingOrder = UUID.randomUUID();
        UUID missingUser = UUID.randomUUID();
        nothingExistsFor(missingOrder, missingUser);

        NoSuchElementException e = assertThrows(NoSuchElementException.class,
                () -> run(t, missingUser, missingOrder));

        assertEquals("user not found", e.getMessage());
        verify(orderService, never()).load(any());
        verify(orderService, never()).loadForUpdate(any());
    }

    private void run(Transition t, UUID caller, UUID orderId) {
        switch (t) {
            case ACCEPT -> facade.acceptOrder(caller, orderId);
            case REJECT -> facade.rejectOrder(caller, orderId, "Out of ingredients");
            case CANCEL -> facade.cancelOrder(caller, orderId);
            case PREPARE -> facade.prepareOrder(caller, orderId);
            case READY -> facade.markOrderReady(caller, orderId);
            case PICKUP -> facade.pickUpOrder(caller, orderId);
            case DELIVER -> facade.deliverOrder(caller, orderId);
            case REFUND -> facade.refundOrder(caller, orderId);
        }
    }

    private UUID rightCaller(Transition t) {
        return switch (t) {
            case ACCEPT, REJECT, PREPARE, READY -> restaurantId;
            case CANCEL -> customerId;
            case PICKUP, DELIVER -> courierId;
            case REFUND -> operatorId;
        };
    }

    private Order arrangeLegal(Transition t) {
        return switch (t) {
            case ACCEPT, REJECT, CANCEL -> orderIn("PlacedState");
            case PREPARE -> orderIn("AcceptedState");
            case READY -> orderIn("PreparingState");
            case PICKUP -> { courierLoggedIn(courierId, CourierState.ASSIGNED); yield orderIn("ReadyState"); }
            case DELIVER -> { courierLoggedIn(courierId, CourierState.CARRYING); yield orderIn("PickedUpState"); }
            case REFUND -> orderIn("DeliveredState");
        };
    }

    private UUID wrongCaller(Transition t) {
        UUID other = UUID.randomUUID();
        switch (t) {
            case ACCEPT, REJECT, PREPARE, READY ->
                    when(regFacade.getCurrentUser(other)).thenReturn(owner(other));
            case CANCEL -> when(regFacade.getCurrentUser(other)).thenReturn(customer(other));
            case PICKUP -> courierLoggedIn(other, CourierState.ASSIGNED);
            case DELIVER -> courierLoggedIn(other, CourierState.CARRYING);
            case REFUND -> { return customerId; }
        }
        return other;
    }

    // accept and prepare put the order first in line so the queue guard passes
    private Order arrangeIllegal(Transition t) {
        switch (t) {
            case ACCEPT -> {
                Order order = storedOrder();
                order.accept();
                queues.placed(restaurantId).enqueue(order.getId());
                return order;
            }
            case REJECT -> { return orderIn("AcceptedState"); }
            case CANCEL -> {
                Order order = storedOrder();
                order.reject("Closed");
                return order;
            }
            case PREPARE -> {
                Order order = orderIn("PlacedState");
                queues.preparation(restaurantId).enqueue(order.getId());
                return order;
            }
            case READY -> { return orderIn("PlacedState"); }
            case PICKUP -> {
                courierLoggedIn(courierId, CourierState.CARRYING);
                return orderIn("DeliveredState");
            }
            case DELIVER -> {
                courierLoggedIn(courierId, CourierState.ASSIGNED);
                return orderIn("ReadyState");
            }
            default -> throw new IllegalArgumentException(t + " has no reachable illegal transition");
        }
    }

    private void assertNothingChanged(Order order, String stateBefore, String queuesBefore, MenuItem item) {
        assertEquals(stateBefore, order.getState().toString(), "state changed");
        assertEquals(queuesBefore, queuesOf(order.getId()), "queues changed");
        assertEquals(List.of(), timeline, "something was saved, published or restocked");
        verify(orderService, never()).save(any());
        verifyNoInteractions(subscriber, eventPublisher);
        verify(menuService, never()).save(any());
        assertEquals(3, item.getStockQuantity(), "stock changed");
    }

    private Order orderIn(String stateName) {
        Order order = storedOrder();
        List<Consumer<Order>> steps = List.of(
                Order::accept,
                Order::startPreparing,
                o -> { o.markReady(); o.assignCourier(courierId); },
                Order::pickUp,
                Order::deliver);
        for (Consumer<Order> step : steps) {
            if (order.getState().toString().equals(stateName)) {
                break;
            }
            step.accept(order);
        }
        assertEquals(stateName, order.getState().toString(), "test setup did not reach " + stateName);

        UUID id = order.getId();
        switch (stateName) {
            case "PlacedState" -> queues.placed(restaurantId).enqueue(id);
            case "AcceptedState" -> queues.preparation(restaurantId).enqueue(id);
            case "ReadyState" -> {
                queues.ready(restaurantId).enqueue(id);
                queues.enqueueGlobalReady(id);
            }
            default -> {
                // in no queue
            }
        }
        return order;
    }

    private Order storedOrder() {
        Order order = new Order(customerId, restaurantId,
                List.of(new OrderLine(menuItemId, 1, new BigDecimal("10.00"), null)), new BigDecimal("10.00"));
        when(orderService.load(order.getId())).thenReturn(order);
        when(orderService.loadForUpdate(order.getId())).thenReturn(order);
        watched = order.getId();
        return order;
    }

    private MenuItem realItem(int stock) {
        MenuItem item = new MenuItem(menuItemId, restaurantId, "Pizza", new BigDecimal("10.00"), stock,
                MenuItemStatus.AVAILABLE, "Cheese", null, Instant.now());
        when(menuService.load(menuItemId)).thenReturn(item);
        when(menuService.loadForUpdate(menuItemId)).thenReturn(item);
        return item;
    }

    private void nothingExistsFor(UUID orderId, UUID userId) {
        when(orderService.load(orderId)).thenThrow(new NoSuchElementException("order not found"));
        when(orderService.loadForUpdate(orderId)).thenThrow(new NoSuchElementException("order not found"));
        when(regFacade.getCurrentUser(userId)).thenThrow(new NoSuchElementException("user not found"));
    }

    private String queuesOf(UUID orderId) {
        List<String> in = new ArrayList<>();
        if (queues.placed(restaurantId).contains(orderId)) in.add("placed");
        if (queues.preparation(restaurantId).contains(orderId)) in.add("preparation");
        if (queues.ready(restaurantId).contains(orderId)) in.add("ready");
        if (queues.globalReadySnapshot().contains(orderId)) in.add("globalReady");
        return in.toString();
    }

    private OrderStateChanged event(Order order, String before, String after, UUID by, String action) {
        return new OrderStateChanged(order.getId(), before, after, by, action);
    }

    private void courierLoggedIn(UUID id, CourierState state) {
        when(regFacade.getCurrentUser(id)).thenReturn(new Courier(id, OffsetDateTime.now(), "A", "B",
                "a@b.c", "ab", "hash", state, new Location(45.5, -73.6)));
    }

    private Customer customer(UUID id) {
        return new Customer(id, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                "123 Street", new Location(45.5, -73.6));
    }

    private Restaurant owner(UUID id) {
        return new Restaurant(id, OffsetDateTime.now(), "A", "B", "a@b.c", "ab", "hash",
                "Resto", RestaurantStatus.OPEN, null);
    }
}
