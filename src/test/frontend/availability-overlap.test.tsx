import { act, renderHook } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { useOrderAvailability } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/menu/useOrderAvailability";
import type { Cart } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/menu/types/cart";

const cart: Cart = {
  restaurantId: "restaurant-1",
  items: [{
    menuItem: {
      id: "item-1",
      name: "Soup",
      price: 10,
      status: "AVAILABLE",
      description: null,
    },
    quantity: 1,
  }],
};
const json = (data: unknown) => new Response(JSON.stringify(data));
// Uses one available selection and mocked responses without touching shared services.

it.each(["polling", "focus"])("keeps a placement check active during %s refresh", async (trigger) => {
  vi.useFakeTimers();
  let menuRequests = 0;
  let finishPlacementCheck!: (response: Response) => void;
  let placementSignal: AbortSignal | null | undefined;
  const availableMenu = [{ id: "item-1", status: "AVAILABLE" }];
  const fetchMock = vi.fn(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/api/restaurants")) {
      return json([{ id: "restaurant-1", status: "OPEN" }]);
    }

    menuRequests++;
    if (menuRequests === 2) {
      placementSignal = options?.signal;
      return new Promise<Response>((resolve) => { finishPlacementCheck = resolve; });
    }

    return json(availableMenu);
  });
  vi.stubGlobal("fetch", fetchMock);
  // Holds the explicit placement lookup open so background events overlap it deterministically.

  const view = renderHook(() => useOrderAvailability(cart, "token"));
  await act(async () => { await vi.advanceTimersByTimeAsync(0); });
  expect(view.result.current.allowed).toBe(true);

  let placementCheck!: Promise<boolean>;
  await act(async () => {
    placementCheck = view.result.current.refreshAvailability();
    await vi.advanceTimersByTimeAsync(0);
  });
  expect(menuRequests).toBe(2);
  expect(view.result.current.checking).toBe(true);
  // Starts the same explicit validation that CartPage awaits before placing an order.

  await act(async () => {
    if (trigger === "polling") {
      await vi.advanceTimersByTimeAsync(15_000);
    } else {
      window.dispatchEvent(new Event("focus"));
    }
  });
  expect(placementSignal?.aborted).toBe(false);
  expect(fetchMock).toHaveBeenCalledTimes(4);
  // Background work must neither abort the placement check nor start a competing lookup.

  await act(async () => {
    finishPlacementCheck(json(availableMenu));
    expect(await placementCheck).toBe(true);
  });
  expect(view.result.current.allowed).toBe(true);
  expect(view.result.current.checking).toBe(false);
  // The original caller receives the successful decision instead of silently abandoning placement.

  await act(async () => { await vi.advanceTimersByTimeAsync(15_000); });
  expect(menuRequests).toBe(3);
  // Finishing the explicit check releases ownership so later background refreshes can still run.
});
// Covers both background triggers that previously canceled an otherwise valid placement attempt.
