package com.mosbyboys.dishesbecrazy.domain.ord.state;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.function.Function;
import java.util.stream.Stream;

class OrderStateTest {

    static Stream<Arguments> legalMoves() {
        return Stream.of(
            Arguments.of(new PlacedState(),
                        (Function<OrderState, OrderState>) OrderState::accept, 
                        AcceptedState.class), // placed --> accepted
            Arguments.of(new PlacedState(),
                        (Function<OrderState, OrderState>) OrderState::reject,
                        RejectedState.class), // placed --> rejected
            Arguments.of(new PlacedState(),
                        (Function<OrderState, OrderState>) OrderState::cancel,
                        CancelledState.class), // placed --> cancelled
            Arguments.of(new AcceptedState(),
                        (Function<OrderState, OrderState>) OrderState::startPreparing, 
                        PreparingState.class), // accepted --> preparing
            Arguments.of(new AcceptedState(),
                        (Function<OrderState, OrderState>) OrderState::cancel, 
                        CancelledState.class), // accepted --> cancel
            Arguments.of(new PreparingState(),
                        (Function<OrderState, OrderState>) OrderState::markReady, 
                        ReadyState.class), // preparing --> ready
            Arguments.of(new PreparingState(),
                        (Function<OrderState, OrderState>) OrderState::cancel, 
                        CancelledState.class), // preparing --> cancel
            Arguments.of(new ReadyState(),
                        (Function<OrderState, OrderState>) OrderState::cancel, 
                        CancelledState.class) // ready --> cancel
        );
    }

    @ParameterizedTest
    @MethodSource("legalMoves")
    void legalMoveReachesExpectedState(OrderState start, Function<OrderState, OrderState> action, Class<?> expected) {
        OrderState result = action.apply(start);
        assertInstanceOf(expected, result);
    }
}