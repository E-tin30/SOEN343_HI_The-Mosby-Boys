import { loggedFetch, readJson } from "../../api/loggedFetch";
import type { Cart } from "../types/cart";

const API = "http://localhost:8080";
const storageKey = (customerId: string) => `dishes.checkout.${customerId}`;
type CheckoutStart = { checkoutId: string; clientSecret: string | null; orderId: string | null; totalMinor: number; cancelled: boolean };

function checkoutIdFor(cart: Cart, customerId: string): string {
  const selection = JSON.stringify({
    restaurantId: cart.restaurantId,
    items: cart.items.map((item) => ({ id: item.menuItem.id, quantity: item.quantity }))
      .sort((left, right) => left.id.localeCompare(right.id)),
  });
  const saved = localStorage.getItem(storageKey(customerId));
  if (saved) {
    try {
      const value: unknown = JSON.parse(saved);
      if (value && typeof value === "object" && "selection" in value && value.selection === selection
          && "id" in value && typeof value.id === "string") return value.id;
    } catch {
      // An unreadable local record contains no server/payment state and can be replaced before checkout starts.
    }
  }
  const id = crypto.randomUUID();
  localStorage.setItem(storageKey(customerId), JSON.stringify({ id, selection }));
  return id;
}
// Retains a non-secret checkout ID across reloads, so a lost response does not silently start another payment.

export function forgetCheckout(customerId: string, checkoutId: string) {
  const saved = localStorage.getItem(storageKey(customerId));
  if (!saved) return;
  try {
    const value: unknown = JSON.parse(saved);
    if (value && typeof value === "object" && "id" in value && value.id === checkoutId)
      localStorage.removeItem(storageKey(customerId));
  } catch {
    localStorage.removeItem(storageKey(customerId));
  }
}
// Only a confirmed order/cancellation may forget an attempt; another tab's newer checkout is preserved.

async function post(path: string, token: string, body: unknown): Promise<Response> {
  return loggedFetch(`${API}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
}

export async function startCheckout(cart: Cart, token: string, customerId: string): Promise<CheckoutStart> {
  const checkoutId = checkoutIdFor(cart, customerId);
  const response = await post("/payments/create-payment-intent", token, {
    checkoutId,
    restaurantId: cart.restaurantId,
    items: cart.items.map((item) => ({ menuItemId: item.menuItem.id, quantity: item.quantity })),
  });
  const value = await readJson<CheckoutStart>(response);
  if (!value || value.checkoutId !== checkoutId || (value.orderId != null && value.orderId !== checkoutId)
      || !Number.isSafeInteger(value.totalMinor) || value.totalMinor < 50) {
    throw new Error("Checkout response could not be verified. Retry this checkout.");
  }
  if (value.cancelled === true) {
    forgetCheckout(customerId, checkoutId);
    throw new Error("The previous checkout was cancelled. Retry to start a new checkout.");
  }
  if (value.orderId !== checkoutId && (typeof value.clientSecret !== "string" || !value.clientSecret)) {
    throw new Error("Checkout response could not be verified. Retry this checkout.");
  }
  return value;
}

export async function completeCheckout(checkoutId: string, token: string): Promise<string> {
  const value = await readJson<{ id: string }>(await post("/placeOrder", token, { checkoutId }));
  if (!value || value.id !== checkoutId) throw new Error("Order confirmation could not be verified. Retry this checkout.");
  return value.id;
}

export async function cancelCheckout(checkoutId: string, token: string): Promise<string | null> {
  const value = await readJson<{ orderId: string | null }>(await post(`/payments/${checkoutId}/cancel`, token, {}));
  if (!value || (value.orderId !== null && value.orderId !== checkoutId)) {
    throw new Error("Cancellation could not be verified. Retry this checkout.");
  }
  return value.orderId;
}
// Server confirmations, not browser assumptions about Stripe, decide whether the cart can be cleared or edited again.
