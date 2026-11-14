package com.mosbyboys.dishesbecrazy.domain.ord.state;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.function.Function;
import java.util.stream.Stream;

class OrderStateTest {

    // Legal preparation transitions and the delivered-to-refunded transition.
    static Stream<Arguments> legalMoves() {
        return Stream.of(
            // PlacedState: only accept, reject and cancel are legal
            Arguments.of(new PlacedState(), (Function<OrderState, OrderState>) OrderState::accept, AcceptedState.class), // placed --> accepted
            Arguments.of(new PlacedState(), (Function<OrderState, OrderState>) OrderState::reject, RejectedState.class), // placed --> rejected
            Arguments.of(new PlacedState(), (Function<OrderState, OrderState>) OrderState::cancel,CancelledState.class), // placed --> cancelled
            
            // AcceptedState: only startPreparing and cancel are legal
            Arguments.of(new AcceptedState(), (Function<OrderState, OrderState>) OrderState::startPreparing, PreparingState.class), // accepted --> preparing
            Arguments.of(new AcceptedState(), (Function<OrderState, OrderState>) OrderState::cancel, CancelledState.class), // accepted --> cancel
            
            // PreparingState: only markReady and cancel are legal
            Arguments.of(new PreparingState(), (Function<OrderState, OrderState>) OrderState::markReady, ReadyState.class), // preparing --> ready
            Arguments.of(new PreparingState(), (Function<OrderState, OrderState>) OrderState::cancel, CancelledState.class), // preparing --> cancel
            
            // ReadyState: only cancel is legal
            Arguments.of(new ReadyState(),(Function<OrderState, OrderState>) OrderState::cancel, CancelledState.class), // ready --> cancel
            Arguments.of(new DeliveredState(), (Function<OrderState, OrderState>) OrderState::refund, RefundedState.class)
        );
    }

    @ParameterizedTest(name = "{0} can {2}")
    @MethodSource("legalMoves")
    void legalMoveReachesExpectedState(OrderState start, Function<OrderState, OrderState> action, Class<?> expected) {
        OrderState result = action.apply(start);
        assertInstanceOf(expected, result);
    }

    // Illegal preparation transitions and attempts to resume or refund an already-refunded order.
    static Stream<Arguments> illegalMoves() {
        return Stream.of(
            // PlacedState: only accept, reject and cancel are legal
            Arguments.of(new PlacedState(), "startPreparing", (Function<OrderState, OrderState>) OrderState::startPreparing),
            Arguments.of(new PlacedState(), "markReady", (Function<OrderState, OrderState>) OrderState::markReady),

            // AcceptedState: only startPreparing and cancel are legal
            Arguments.of(new AcceptedState(), "accept", (Function<OrderState, OrderState>) OrderState::accept),
            Arguments.of(new AcceptedState(), "reject", (Function<OrderState, OrderState>) OrderState::reject),
            Arguments.of(new AcceptedState(), "markReady", (Function<OrderState, OrderState>) OrderState::markReady),

            // PreparingState: only markReady and cancel are legal
            Arguments.of(new PreparingState(), "accept", (Function<OrderState, OrderState>) OrderState::accept),
            Arguments.of(new PreparingState(), "reject", (Function<OrderState, OrderState>) OrderState::reject),
            Arguments.of(new PreparingState(), "startPreparing", (Function<OrderState, OrderState>) OrderState::startPreparing),

            // ReadyState: only cancel is legal
            Arguments.of(new ReadyState(), "accept", (Function<OrderState, OrderState>) OrderState::accept),
            Arguments.of(new ReadyState(), "reject", (Function<OrderState, OrderState>) OrderState::reject),
            Arguments.of(new ReadyState(), "startPreparing", (Function<OrderState, OrderState>) OrderState::startPreparing),
            Arguments.of(new ReadyState(), "markReady", (Function<OrderState, OrderState>) OrderState::markReady),

            // RejectedState: terminal, nothing is legal
            Arguments.of(new RejectedState(), "accept", (Function<OrderState, OrderState>) OrderState::accept),
            Arguments.of(new RejectedState(), "reject", (Function<OrderState, OrderState>) OrderState::reject),
            Arguments.of(new RejectedState(), "startPreparing", (Function<OrderState, OrderState>) OrderState::startPreparing),
            Arguments.of(new RejectedState(), "markReady", (Function<OrderState, OrderState>) OrderState::markReady),
            Arguments.of(new RejectedState(), "cancel", (Function<OrderState, OrderState>) OrderState::cancel),

            // CancelledState: terminal, nothing is legal
            Arguments.of(new CancelledState(), "accept", (Function<OrderState, OrderState>) OrderState::accept),
            Arguments.of(new CancelledState(), "reject", (Function<OrderState, OrderState>) OrderState::reject),
            Arguments.of(new CancelledState(), "startPreparing", (Function<OrderState, OrderState>) OrderState::startPreparing),
            Arguments.of(new CancelledState(), "markReady", (Function<OrderState, OrderState>) OrderState::markReady),
            Arguments.of(new CancelledState(), "cancel", (Function<OrderState, OrderState>) OrderState::cancel),

            Arguments.of(new RefundedState(), "pickUp", (Function<OrderState, OrderState>) OrderState::pickUp),
            Arguments.of(new RefundedState(), "refund", (Function<OrderState, OrderState>) OrderState::refund)
        );
    }

    @ParameterizedTest(name = "{0} cannot {1}")
    @MethodSource("illegalMoves")
    void illegalMoveThrows(OrderState start, String actionName, Function<OrderState, OrderState> action) {
        assertThrows(IllegalTransitionException.class, () -> action.apply(start));
    }
}
