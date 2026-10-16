package com.mosbyboys.dishesbecrazy.domain.ord.state;

/* Parent of all order states. Every action (accept, reject, startPreparing, markReady, cancel)
throws an exception by default, and each state only overrides the ones that are legal from it (R-ORD-04). */
public abstract class OrderState {

    // Illegal by default. A state overrides this if accept is allowed from it.
    public OrderState accept() {
        throw new IllegalTransitionException(this, "accept");
    }

    // Illegal by default. A state overrides this if reject is allowed from it.
    public OrderState reject() {
        throw new IllegalTransitionException(this, "reject");
    }

    // Illegal by default. A state overrides this if startPreparing is allowed from it.
    public OrderState startPreparing() {
        throw new IllegalTransitionException(this, "startPreparing");
    }

    // Illegal by default. A state overrides this if markReady is allowed from it.
    public OrderState markReady() {
        throw new IllegalTransitionException(this, "markReady");
    }

    // Illegal by default. A state overrides this if cancel is allowed from it.
    // Who is allowed to cancel (customer vs operator) is PermissionPolicy's job, not this class.
    public OrderState cancel() {
        throw new IllegalTransitionException(this, "cancel");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
