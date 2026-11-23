package com.mosbyboys.dishesbecrazy.application.ord;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

import com.mosbyboys.dishesbecrazy.application.prc.PricingService;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Location;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.ord.Restaurant;
import com.mosbyboys.dishesbecrazy.domain.ord.RestaurantStatus;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import com.mosbyboys.dishesbecrazy.domain.reg.Courier;
import com.mosbyboys.dishesbecrazy.domain.reg.CourierState;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.domain.reg.Operator;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.domain.reg.User;

class ORDFacadeQueriesTest {

    private OrderService orderService;
    private MenuService menuService;
    private RestaurantService restaurantService;
    private PricingService pricingService;
    private REGFacade regFacade;
    private OrderQueues queues;
    private ORDFacade facade;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID otherRestaurantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID otherCustomerId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();
    private final UUID pizza = UUID.randomUUID();
    private final UUID salad = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        menuService = mock(MenuService.class);
        restaurantService = mock(RestaurantService.class);
        pricingService = mock(PricingService.class);
        regFacade = mock(REGFacade.class);
        queues = new OrderQueues();
        facade = newFacade();
    }

    private ORDFacade newFacade() {
        return new ORDFacade(orderService, new PermissionPolicy(), queues, menuService,
                restaurantService,
                new OrderQueries(orderService, queues, menuService, restaurantService, pricingService, regFacade),
                regFacade, mock(ApplicationEventPublisher.class));
    }


    @ParameterizedTest
    @EnumSource(Role.class)
    void historyAsksOnlyForTheCallersOwnScope(Role role) {
        User user = userWith(role);

        facade.getOrderHistory(user);

        switch (role) {
            case CUSTOMER -> verify(orderService).findHistoryForCustomer(customerId);
            case RESTAURANT -> verify(orderService).findHistoryForRestaurant(restaurantId);
            case COURIER -> verify(orderService).findHistoryForCourier(courierId);
            case OPERATOR -> verify(orderService).findHistoryForAll();
        }
        verifyNoMoreInteractions(orderService);
    }

    @Test
    void historyKeepsTheFinderOrderAndAttachesNamesAndPricingToEveryView() {
        BigDecimal tenDollars = new BigDecimal("10.00");
        Order first = order(customerId, restaurantId, tenDollars, pizza);
        Order second = order(otherCustomerId, otherRestaurantId, tenDollars, salad);
        PricingBreakdown breakdown = breakdown(tenDollars);
        when(orderService.findHistoryForAll()).thenReturn(List.of(first, second));
        Map<UUID, String> itemNames = Map.of(pizza, "Pizza", salad, "Salad");
        Map<UUID, String> restaurantNames = Map.of(restaurantId, "Resto", otherRestaurantId, "Other");
        Map<UUID, String> customerNames = Map.of(customerId, "A B", otherCustomerId, "C D");
        when(menuService.getNames(any())).thenReturn(itemNames);
        when(restaurantService.getNames(any())).thenReturn(restaurantNames);
        when(regFacade.getDisplayNames(any())).thenReturn(customerNames);
        when(pricingService.calculateTotal(tenDollars)).thenReturn(breakdown);

        List<OrderHistoryView> views = facade.getOrderHistory(userWith(Role.OPERATOR));

        assertEquals(List.of(
                new OrderHistoryView(first, itemNames, restaurantNames, customerNames, breakdown),
                new OrderHistoryView(second, itemNames, restaurantNames, customerNames, breakdown)), views);
    }

    @Test
    void historyLoadsEachKindOfNameOnceForAllOrdersWithoutDuplicates() {
        when(orderService.findHistoryForAll()).thenReturn(List.of(
                order(customerId, restaurantId, null, pizza, salad),
                order(customerId, restaurantId, null, pizza),
                order(otherCustomerId, otherRestaurantId, null, salad)));

        facade.getOrderHistory(userWith(Role.OPERATOR));

        verify(menuService).getNames(Set.of(pizza, salad));
        verify(restaurantService).getNames(Set.of(restaurantId, otherRestaurantId));
        verify(regFacade).getDisplayNames(Set.of(customerId, otherCustomerId));
        verifyNoMoreInteractions(menuService, restaurantService);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void onlyOperatorsAndRestaurantsSeeCustomerNames(Role role) {
        Order order = order(customerId, restaurantId, null, pizza);
        when(orderService.findHistoryForCustomer(any())).thenReturn(List.of(order));
        when(orderService.findHistoryForRestaurant(any())).thenReturn(List.of(order));
        when(orderService.findHistoryForCourier(any())).thenReturn(List.of(order));
        when(orderService.findHistoryForAll()).thenReturn(List.of(order));
        when(regFacade.getDisplayNames(any())).thenReturn(Map.of(customerId, "A B"));

        List<OrderHistoryView> views = facade.getOrderHistory(userWith(role));

        boolean seesCustomers = role == Role.OPERATOR || role == Role.RESTAURANT;
        assertEquals(seesCustomers ? Map.of(customerId, "A B") : Map.of(), views.get(0).customerNames());
        verify(regFacade, times(seesCustomers ? 1 : 0)).getDisplayNames(any());
    }

    @Test
    void anOrderWithoutASubtotalHasNoPricingAndIsNeverPriced() {
        BigDecimal tenDollars = new BigDecimal("10.00");
        Order priced = order(customerId, restaurantId, tenDollars, pizza);
        Order unpriced = order(customerId, restaurantId, null, pizza);
        when(orderService.findHistoryForCustomer(customerId)).thenReturn(List.of(priced, unpriced));
        when(pricingService.calculateTotal(tenDollars)).thenReturn(breakdown(tenDollars));

        List<OrderHistoryView> views = facade.getOrderHistory(userWith(Role.CUSTOMER));

        assertEquals(breakdown(tenDollars), views.get(0).pricing());
        assertNull(views.get(1).pricing());
        verify(pricingService).calculateTotal(tenDollars);
        verifyNoMoreInteractions(pricingService);
    }

    @Test
    void anEmptyHistoryGivesNoViews() {
        when(orderService.findHistoryForCustomer(customerId)).thenReturn(List.of());

        assertEquals(List.of(), facade.getOrderHistory(userWith(Role.CUSTOMER)));
    }


    @Test
    void aCourierSeesTheirCurrentOrdersBuiltLikeHistoryButWithoutCustomerNames() {
        BigDecimal tenDollars = new BigDecimal("10.00");
        Order order = order(customerId, restaurantId, tenDollars, pizza);
        when(orderService.findCurrentForCourier(courierId)).thenReturn(List.of(order));
        when(menuService.getNames(any())).thenReturn(Map.of(pizza, "Pizza"));
        when(restaurantService.getNames(any())).thenReturn(Map.of(restaurantId, "Resto"));
        when(pricingService.calculateTotal(tenDollars)).thenReturn(breakdown(tenDollars));

        List<OrderHistoryView> views = facade.getCourierCurrentOrders(userWith(Role.COURIER));

        assertEquals(List.of(new OrderHistoryView(order, Map.of(pizza, "Pizza"),
                Map.of(restaurantId, "Resto"), Map.of(), breakdown(tenDollars))), views);
        verify(orderService).findCurrentForCourier(courierId);
        verifyNoMoreInteractions(orderService);
        verify(regFacade, never()).getDisplayNames(any());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "COURIER", mode = EnumSource.Mode.EXCLUDE)
    void onlyACourierHasCurrentOrders(Role role) {
        User user = userWith(role);

        assertThrows(PermissionDeniedException.class, () -> facade.getCourierCurrentOrders(user));

        verifyNoInteractions(orderService, menuService, restaurantService, pricingService);
    }


    @Test
    void aRestaurantAsksForItsOwnOrdersInTheFourQueueStates() {
        facade.getRestaurantOrders(userWith(Role.RESTAURANT));

        verify(orderService).findForRestaurant(restaurantId,
                List.of("PlacedState", "AcceptedState", "PreparingState", "ReadyState"));
        verifyNoMoreInteractions(orderService);
    }

    @Test
    void restaurantViewsCarryItemNamesLoadedOnceAndMarkTheHeadOfEachLine() {
        Order nextToAccept = order(customerId, restaurantId, null, pizza);
        Order waitingToAccept = order(customerId, restaurantId, null, salad);
        Order nextToPrepare = order(customerId, restaurantId, null, pizza);
        Order waitingToPrepare = order(customerId, restaurantId, null, pizza);
        queues.placed(restaurantId).enqueue(nextToAccept.getId());
        queues.placed(restaurantId).enqueue(waitingToAccept.getId());
        queues.preparation(restaurantId).enqueue(nextToPrepare.getId());
        queues.preparation(restaurantId).enqueue(waitingToPrepare.getId());
        when(orderService.findForRestaurant(any(), any())).thenReturn(
                List.of(nextToAccept, waitingToAccept, nextToPrepare, waitingToPrepare));
        Map<UUID, String> names = Map.of(pizza, "Pizza", salad, "Salad");
        when(menuService.getNames(any())).thenReturn(names);

        List<RestaurantOrderView> views = facade.getRestaurantOrders(userWith(Role.RESTAURANT));

        assertEquals(List.of(
                new RestaurantOrderView(nextToAccept, names, true, false),
                new RestaurantOrderView(waitingToAccept, names, false, false),
                new RestaurantOrderView(nextToPrepare, names, false, true),
                new RestaurantOrderView(waitingToPrepare, names, false, false)), views);
        verify(menuService).getNames(Set.of(pizza, salad));
        verifyNoMoreInteractions(menuService);
    }

    @Test
    void withEmptyLinesNoOrderIsNextInEither() {
        Order order = order(customerId, restaurantId, null, pizza);
        when(orderService.findForRestaurant(any(), any())).thenReturn(List.of(order));

        List<RestaurantOrderView> views = facade.getRestaurantOrders(userWith(Role.RESTAURANT));

        assertFalse(views.get(0).nextToAccept());
        assertFalse(views.get(0).nextToPrepare());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "RESTAURANT", mode = EnumSource.Mode.EXCLUDE)
    void onlyARestaurantCanViewItsQueues(Role role) {
        User user = userWith(role);

        assertThrows(PermissionDeniedException.class, () -> facade.getRestaurantOrders(user));

        verifyNoInteractions(orderService, menuService);
    }


    private Order order(UUID customer, UUID restaurant, BigDecimal subtotal, UUID... itemIds) {
        List<OrderLine> lines = Arrays.stream(itemIds)
                .map(id -> new OrderLine(id, 1, new BigDecimal("5.00"), null))
                .toList();
        return new Order(customer, restaurant, lines, subtotal);
    }

    private PricingBreakdown breakdown(BigDecimal subtotal) {
        return new PricingBreakdown.Builder().subtotal(subtotal).total(subtotal.add(BigDecimal.ONE)).build();
    }

    private User userWith(Role role) {
        OffsetDateTime now = OffsetDateTime.now();
        return switch (role) {
            case CUSTOMER -> new Customer(customerId, now, "A", "B", "a@b.c", "ab", "hash",
                    "123 Street", new Location(45.5, -73.6));
            case RESTAURANT -> new Restaurant(restaurantId, now, "A", "B", "a@b.c", "ab", "hash",
                    "Resto", RestaurantStatus.OPEN, null);
            case COURIER -> new Courier(courierId, now, "A", "B", "a@b.c", "ab", "hash",
                    CourierState.IDLE, new Location(45.5, -73.6));
            case OPERATOR -> new Operator(operatorId, now, "A", "B", "a@b.c", "ab", "hash");
        };
    }
}
