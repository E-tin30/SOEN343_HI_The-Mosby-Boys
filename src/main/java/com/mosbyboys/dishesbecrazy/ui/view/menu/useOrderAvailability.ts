import { useCallback, useEffect, useRef, useState } from "react";
import { getMenu, getRestaurants } from "./api/menuApi";
import type { Cart } from "./types/cart";
import { logError } from "../outputLog";

export type OrderAvailability = {
  allowed: boolean;
  checking: boolean;
  reason: string;
};
// Gives the cart the disabled state, loading state, and explanation for Place Order.

const checking: OrderAvailability = {
  allowed: false, checking: true, reason: "Checking restaurant and item availability…",
};
// Keeps Place Order disabled until the current availability check has completed.

export function useOrderAvailability(cart: Cart | null, token: string | null) {
  const [snapshot, setSnapshot] = useState<{ cart: Cart | null; token: string | null; value: OrderAvailability }>();
  const active = useRef<AbortController | null>(null);
  // Associates results with their cart/session and tracks the request that is still relevant.

  const refresh = useCallback(async (): Promise<boolean> => {
    active.current?.abort();
    const controller = new AbortController();
    active.current = controller;
    // Cancels the previous check so a late response cannot overwrite a newer result.
    const commit = (value: OrderAvailability) => {
      if (!controller.signal.aborted) setSnapshot({ cart, token, value });
      return !controller.signal.aborted && value.allowed;
    };
    // Saves only active results and also returns a boolean for the placement-time recheck.
    commit(checking);
    // Resets the previous success while fresh restaurant and menu data is being requested.
    if (!cart?.items.length || !token) {
      return commit({ ...checking, checking: false, reason: cart?.items.length ? "Sign in to place an order." : "Your cart is empty." });
    }
    // Avoids network requests when there is no selection or authenticated session to check.
    try {
      const restaurants = await getRestaurants(token, controller.signal);
      const restaurant = restaurants.find((r) => r.id === cart.restaurantId);
      // Customer lists contain only open restaurants, so a missing restaurant is unavailable.
      if (!restaurant || restaurant.status !== "OPEN") {
        return commit({ allowed: false, checking: false, reason: "This restaurant is not accepting orders." });
      }
      // Applies R-DASH-04 to paused, closed, or no-longer-listed restaurants.
      const menu = await getMenu(token, cart.restaurantId, controller.signal);
      const allUnavailable = cart.items.every(({ menuItem }) =>
        !menu.some((fresh) => fresh.id === menuItem.id && fresh.status === "AVAILABLE")
      );
      // Blocks the sold-out case only when EVERY selected item lacks an available menu match.
      return commit({
        allowed: !allUnavailable, checking: false,
        reason: allUnavailable ? "All selected items are sold out or unavailable." : "",
      });
      // Mixed selections do not receive an additional UI restriction; ORD still validates submission.
    } catch (error) {
      if (controller.signal.aborted) return false;
      logError(error, "Could not check order availability.");
      return commit({ allowed: false, checking: false, reason: "Could not check availability. Please retry." });
      // Leaves placement disabled on a genuine lookup failure and records it in the output console.
    }
  }, [cart, token]);

  useEffect(() => {
    void refresh();
    const onFocus = () => { void refresh(); };
    const timer = window.setInterval(onFocus, 15_000);
    window.addEventListener("focus", onFocus);
    // Refreshes on selection/session changes, window focus, and a 15-second interval.
    return () => {
      active.current?.abort();
      window.clearInterval(timer);
      window.removeEventListener("focus", onFocus);
    };
    // Removes obsolete requests, timers, and listeners when this check is replaced or unmounted.
  }, [refresh]);

  const availability = snapshot?.cart === cart && snapshot?.token === token ? snapshot.value : checking;
  // Prevents a previous cart/session's success from briefly enabling Place Order for a new one.
  return { ...availability, refresh };
}
