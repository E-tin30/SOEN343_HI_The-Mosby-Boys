import { useCallback, useEffect, useRef, useState } from "react";
import { getMenu, getRestaurants } from "./api/menuApi";
import type { Cart } from "./types/cart";
import { logError } from "../outputLog";

export type OrderAvailability = {
  allowed: boolean;
  checking: boolean;
  reason: string;
  unavailableIds: string[];
};

const checking: OrderAvailability = {
  allowed: false, checking: true, reason: "Checking restaurant and item availability…", unavailableIds: [],
};

export function useOrderAvailability(cart: Cart | null, token: string | null) {
  const [snapshot, setSnapshot] = useState<{ cart: Cart | null; token: string | null; value: OrderAvailability }>();
  const active = useRef<AbortController | null>(null);

  const refresh = useCallback(async (): Promise<boolean> => {
    active.current?.abort();
    const controller = new AbortController();
    active.current = controller;
    const commit = (value: OrderAvailability) => {
      if (!controller.signal.aborted) setSnapshot({ cart, token, value });
      return !controller.signal.aborted && value.allowed;
    };
    commit(checking);
    if (!cart?.items.length || !token) {
      return commit({ ...checking, checking: false, reason: cart?.items.length ? "Sign in to place an order." : "Your cart is empty." });
    }
    try {
      // Customer restaurant lists contain only OPEN restaurants. A missing entry is unavailable.
      const restaurants = await getRestaurants(token, controller.signal);
      const restaurant = restaurants.find((r) => r.id === cart.restaurantId);
      if (!restaurant || restaurant.status !== "OPEN") {
        return commit({ allowed: false, checking: false, reason: "This restaurant is not accepting orders.", unavailableIds: [] });
      }
      const menu = await getMenu(token, cart.restaurantId, controller.signal);
      const unavailableIds = cart.items.filter(({ menuItem }) =>
        !menu.some((fresh) => fresh.id === menuItem.id && fresh.status === "AVAILABLE")
      ).map(({ menuItem }) => menuItem.id);
      return commit({
        allowed: unavailableIds.length === 0, checking: false, unavailableIds,
        reason: unavailableIds.length === 0 ? ""
          : unavailableIds.length === cart.items.length ? "All selected items are sold out or unavailable."
          : "Remove the unavailable items before placing your order.",
      });
    } catch (error) {
      if (controller.signal.aborted) return false;
      logError(error, "Could not check order availability.");
      return commit({ allowed: false, checking: false, reason: "Could not check availability. Please retry.", unavailableIds: [] });
    }
  }, [cart, token]);

  useEffect(() => {
    void refresh();
    const onFocus = () => { void refresh(); };
    const timer = window.setInterval(onFocus, 15_000);
    window.addEventListener("focus", onFocus);
    return () => {
      active.current?.abort();
      window.clearInterval(timer);
      window.removeEventListener("focus", onFocus);
    };
  }, [refresh]);

  // Never briefly enable a new cart using the previous cart's successful response.
  const availability = snapshot?.cart === cart && snapshot?.token === token ? snapshot.value : checking;
  return { ...availability, refresh };
}
