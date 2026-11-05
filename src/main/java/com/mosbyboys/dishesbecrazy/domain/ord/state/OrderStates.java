package com.mosbyboys.dishesbecrazy.domain.ord.state;

//String to state factory. Used for loading orders from db.
public final class OrderStates {
    private OrderStates() {}

    public static OrderState fromName(String name) {
        return switch (name) {
            case "AcceptedState"  -> new AcceptedState();
            case "CancelledState" -> new CancelledState();
            case "DeliveredState"  -> new DeliveredState();
            case "PickedUpState"  -> new PickedUpState();
            case "PlacedState"    -> new PlacedState();
            case "PreparingState" -> new PreparingState();
            case "ReadyState"     -> new ReadyState();
            case "RefundedState"  -> new RefundedState();
            case "RejectedState"  -> new RejectedState();
            default -> throw new IllegalStateException("Unknown order state: " + name);
        };
    }
}
