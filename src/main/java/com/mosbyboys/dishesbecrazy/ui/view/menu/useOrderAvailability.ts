import { useCallback, useEffect, useRef, useState } from "react";
import { getMenu, getRestaurants } from "./api/menuApi";
import type { Cart } from "./types/cart";
import { log } from "../outputLog";

export type OrderAvailability = {
  allowed: boolean;
  checking: boolean;
  reason: string;
};
// Gives the cart the disabled state, loading state, and explanation for Place Order.

type OrderAvailabilitySnapshot = {
  cart: Cart | null;
  token: string | null;
  value: OrderAvailability;
};
// Associates each result with the selection and session that were checked.

const CHECKING_AVAILABILITY: OrderAvailability = {
  allowed: false,
  checking: true,
  reason: "Checking restaurant and item availability…",
};
// Keeps Place Order disabled until the current availability check has completed.

export function useOrderAvailability(cart: Cart | null, token: string | null) {
  const [availabilitySnapshot, setAvailabilitySnapshot] = useState<OrderAvailabilitySnapshot>();
  const activeRequest = useRef<AbortController | null>(null);
  // Tracks the result's inputs and the request that may still publish a result.

  const refreshAvailability = useCallback(async (): Promise<boolean> => {
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    // An explicit refresh replaces the previous check, so stale responses cannot overwrite it.

    const updateAvailability = (value: OrderAvailability): boolean => {
      if (controller.signal.aborted) {
        return false;
      }

      setAvailabilitySnapshot({ cart, token, value });
      return value.allowed;
    };
    // Publishes only current results and returns the decision awaited by Place Order.

    updateAvailability(CHECKING_AVAILABILITY);
    // Disables placement while the current restaurant and menu data are being checked.

    try {
      if (!cart?.items.length) {
        return updateAvailability({
          allowed: false,
          checking: false,
          reason: "Your cart is empty.",
        });
      }
      // Avoids availability requests when there is no selected item to order.

      if (!token) {
        return updateAvailability({
          allowed: false,
          checking: false,
          reason: "Sign in to place an order.",
        });
      }
      // Requires an authenticated session before querying customer endpoints.

      const restaurants = await getRestaurants(token, controller.signal);
      const restaurant = restaurants.find((restaurant) => restaurant.id === cart.restaurantId);
      // Customer lists contain only open restaurants, so a missing restaurant is unavailable.

      if (!restaurant || restaurant.status !== "OPEN") {
        return updateAvailability({
          allowed: false,
          checking: false,
          reason: "This restaurant is not accepting orders.",
        });
      }
      // Applies R-DASH-04 to paused, closed, or no-longer-listed restaurants.

      const menu = await getMenu(token, cart.restaurantId, controller.signal);
      const hasAvailableSelectedItem = cart.items.some(({ menuItem: selectedItem }) =>
        menu.some((menuItem) => menuItem.id === selectedItem.id && menuItem.status === "AVAILABLE")
      );
      // One available selected item is enough to avoid the ticket's EVERY-item sold-out restriction.

      return updateAvailability({
        allowed: hasAvailableSelectedItem,
        checking: false,
        reason: hasAvailableSelectedItem ? "" : "All selected items are sold out or unavailable.",
      });
    } catch (error) {
      if (controller.signal.aborted) {
        return false;
      }

      log("error", "Could not check order availability.");
      return updateAvailability({
        allowed: false,
        checking: false,
        reason: "Could not check availability. Please retry.",
      });
    } finally {
      if (activeRequest.current === controller) {
        activeRequest.current = null;
      }
    }
    // Lookup failures stay visible; finishing an old check cannot release a newer request's ownership.
  }, [cart, token]);
  // Keeps the placement-time check tied to the current selection and session.

  useEffect(() => {
    void refreshAvailability();
    // The hook owns initial checks and handles their failures without blocking rendering.

    const refreshInBackground = () => {
      if (activeRequest.current) {
        return;
      }

      void refreshAvailability();
    };
    // Polling and focus skip an active check, including validation awaited by Place Order.

    const timer = window.setInterval(refreshInBackground, 15_000);
    window.addEventListener("focus", refreshInBackground);
    // Keeps the disabled state current between cart edits and explicit placement attempts.

    return () => {
      activeRequest.current?.abort();
      activeRequest.current = null;
      window.clearInterval(timer);
      window.removeEventListener("focus", refreshInBackground);
    };
  }, [refreshAvailability]);
  // Cancels obsolete checks and removes background work when inputs change or the hook unmounts.

  const availability = availabilitySnapshot?.cart === cart && availabilitySnapshot?.token === token
    ? availabilitySnapshot.value
    : CHECKING_AVAILABILITY;
  // Prevents a previous cart/session's success from briefly enabling Place Order for a new one.

  return { ...availability, refreshAvailability };
}
