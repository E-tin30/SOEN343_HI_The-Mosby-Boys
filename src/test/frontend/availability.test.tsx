import { expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { useOrderAvailability } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/menu/useOrderAvailability";
import type { Cart } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/menu/types/cart";

const cart: Cart = { restaurantId: "restaurant-1", items: [{ menuItem: { id: "item-1", name: "Soup", price: 10, status: "AVAILABLE", description: null }, quantity: 1 }] };
const json = (data: unknown) => new Response(JSON.stringify(data));
// Supplies a controlled cart and JSON responses without changing the shared database.

it("ignores an old cart's late response after selection changes", async () => {
  let finishOld!: (r: Response) => void;
  let menus = 0;
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    if (url.endsWith("/api/restaurants")) return json([{ id: "restaurant-1", status: "OPEN" }]);
    menus++;
    if (menus === 1) return new Promise<Response>((resolve) => { finishOld = resolve; });
    return json([{ id: "item-2", status: "SOLD_OUT" }]);
  }));
  const view = renderHook(({ selection }) => useOrderAvailability(selection, "token"), { initialProps: { selection: cart } });
  await waitFor(() => expect(menus).toBe(1));
  const next = { ...cart, items: [{ ...cart.items[0], menuItem: { ...cart.items[0].menuItem, id: "item-2" } }] };
  view.rerender({ selection: next });
  await waitFor(() => expect(view.result.current.reason).toContain("One or more selected items"));
  await act(async () => finishOld(json([{ id: "item-1", status: "AVAILABLE" }])));
  expect(view.result.current.allowed).toBe(false);
  expect(view.result.current.reason).toContain("One or more selected items");
});
// Ensures a delayed response for an old selection cannot re-enable Place Order for the current selection.

it("polls availability and cleans up the timer on unmount", async () => {
  vi.useFakeTimers();
  let open = true;
  const fetchMock = vi.fn(async (url: string) => url.endsWith("/api/restaurants")
    ? json(open ? [{ id: "restaurant-1", status: "OPEN" }] : [])
    : json([{ id: "item-1", status: "AVAILABLE" }]));
  vi.stubGlobal("fetch", fetchMock);
  const view = renderHook(() => useOrderAvailability(cart, "token"));
  await act(async () => { await vi.advanceTimersByTimeAsync(0); });
  expect(view.result.current.allowed).toBe(true);
  open = false;
  await act(async () => { await vi.advanceTimersByTimeAsync(15_000); });
  expect(view.result.current.allowed).toBe(false);
  expect(view.result.current.reason).toContain("not accepting orders");
  view.unmount();
  const count = fetchMock.mock.calls.length;
  await vi.advanceTimersByTimeAsync(30_000);
  expect(fetchMock.mock.calls).toHaveLength(count);
});
// Uses a fake clock to verify polling refreshes availability and stops after unmount.

it.each([null, { ...cart, items: [] }])("keeps an empty cart disabled without requests", async (selection) => {
  const fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
  const view = renderHook(() => useOrderAvailability(selection, "token"));
  await waitFor(() => expect(view.result.current.checking).toBe(false));
  expect(view.result.current.allowed).toBe(false);
  expect(fetchMock).not.toHaveBeenCalled();
});
// Checks both missing and empty carts stay disabled without unnecessary requests.

it("blocks a mixed selection when one selected item is unavailable", async () => {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => url.endsWith("/api/restaurants")
    ? json([{ id: "restaurant-1", status: "OPEN" }])
    : json([{ id: "item-1", status: "AVAILABLE" }, { id: "item-2", status: "SOLD_OUT" }])));
  const selection = { ...cart, items: [...cart.items, { ...cart.items[0], menuItem: { ...cart.items[0].menuItem, id: "item-2" } }] };
  const view = renderHook(() => useOrderAvailability(selection, "token"));
  await waitFor(() => expect(view.result.current.checking).toBe(false));
  expect(view.result.current.allowed).toBe(false);
  expect(view.result.current.reason).toContain("One or more selected items");
});
// Mixed selections cannot proceed to a payment attempt that the backend would reject.
