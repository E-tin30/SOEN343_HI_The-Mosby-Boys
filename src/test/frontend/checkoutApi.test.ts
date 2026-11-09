import { beforeEach, expect, it, vi } from "vitest";
import { completeCheckout, startCheckout } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/menu/api/checkoutApi";
import type { Cart } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/menu/types/cart";

const cart: Cart = { restaurantId: "restaurant-1", items: [{ menuItem: { id: "item-1", name: "Soup", price: 0.01,
  status: "AVAILABLE", description: null }, quantity: 2 }] };
const json = (body: unknown) => new Response(JSON.stringify(body));
beforeEach(() => localStorage.clear());

it("sends IDs and quantities while the backend determines payment prices and ownership", async () => {
  const fetchMock = vi.fn(async (_url: string, options?: RequestInit) => {
    const body = JSON.parse(options!.body as string);
    return json({ checkoutId: body.checkoutId, clientSecret: "test-secret", orderId: null, totalMinor: 1200 });
  });
  vi.stubGlobal("fetch", fetchMock);
  await startCheckout(cart, "token", "customer-1");
  const body = JSON.parse(fetchMock.mock.calls[0][1]!.body as string);
  expect(body.items).toEqual([{ menuItemId: "item-1", quantity: 2 }]);
  expect(body.userId).toBeUndefined();
  expect(body.items[0].price).toBeUndefined();
});

it("does not reuse a customer's attempt for a changed cart or another customer", async () => {
  const ids: string[] = [];
  vi.stubGlobal("fetch", vi.fn(async (_url: string, options?: RequestInit) => {
    const body = JSON.parse(options!.body as string);
    ids.push(body.checkoutId);
    return json({ checkoutId: body.checkoutId, clientSecret: "test-secret", orderId: null, totalMinor: 1200 });
  }));
  await startCheckout(cart, "token", "customer-1");
  await startCheckout(cart, "token", "customer-1");
  await startCheckout({ ...cart, items: [{ ...cart.items[0], quantity: 3 }] }, "token", "customer-1");
  await startCheckout(cart, "other-token", "customer-2");
  expect(ids[0]).toBe(ids[1]);
  expect(new Set([ids[0], ids[2], ids[3]]).size).toBe(3);
});

it("rejects an unrelated completed-order response even when a client secret is present", async () => {
  vi.stubGlobal("fetch", vi.fn(async (_url: string, options?: RequestInit) => {
    const body = JSON.parse(options!.body as string);
    return json({ checkoutId: body.checkoutId, clientSecret: "test-secret", orderId: "unrelated-order", totalMinor: 1200 });
  }));
  await expect(startCheckout(cart, "token", "customer-1")).rejects.toThrow("could not be verified");
});

it("unreadable or mismatched order confirmations retain the checkout for retry", async () => {
  const record = JSON.stringify({ id: "checkout-1", selection: "selection" });
  localStorage.setItem("dishes.checkout.customer-1", record);
  vi.stubGlobal("fetch", vi.fn(async () => json({ id: "another-order" })));
  await expect(completeCheckout("checkout-1", "token")).rejects.toThrow("could not be verified");
  expect(localStorage.getItem("dishes.checkout.customer-1")).toBe(record);
});

it("a confirmed terminal cancellation permits a fresh attempt instead of trapping retries on the old ID", async () => {
  const ids: string[] = [];
  vi.stubGlobal("fetch", vi.fn(async (_url: string, options?: RequestInit) => {
    const body = JSON.parse(options!.body as string);
    ids.push(body.checkoutId);
    return json({ checkoutId: body.checkoutId, clientSecret: ids.length === 1 ? null : "test-secret",
      orderId: null, totalMinor: 1200, cancelled: ids.length === 1 });
  }));
  await expect(startCheckout(cart, "token", "customer-1")).rejects.toThrow("previous checkout was cancelled");
  expect(localStorage.getItem("dishes.checkout.customer-1")).toBeNull();
  await startCheckout(cart, "token", "customer-1");
  expect(ids[0]).not.toBe(ids[1]);
});
