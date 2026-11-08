import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import CartPage from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/menu/CartPage";
import OutputConsole from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/OutputConsole";

const mocks = vi.hoisted(() => ({
  clearCart: vi.fn(),
  payment: vi.fn(),
  cart: { restaurantId: "restaurant-1", items: [
    { menuItem: { id: "item-1", name: "Soup", status: "AVAILABLE", price: 10, description: null }, quantity: 1 },
  ] },
}));
// Holds the test cart and spies for existing cart/payment actions without contacting live services.
vi.mock("../../main/java/com/mosbyboys/dishesbecrazy/ui/view/AuthContext", () => ({
  useAuth: () => ({ accessToken: "private-token", user: { id: "customer-1" } }),
}));
// Supplies a fake authenticated customer so these tests exercise Place Order rather than login.
vi.mock("../../main/java/com/mosbyboys/dishesbecrazy/ui/view/CartContext", () => ({
  useCart: () => ({ cart: mocks.cart, itemCount: 1, clearCart: mocks.clearCart, updateQuantity: vi.fn(), removeItem: vi.fn() }),
}));
// Exposes a controlled selection and cart commands for each availability scenario.
vi.mock("@stripe/stripe-js", () => ({ loadStripe: () => Promise.resolve(null) }));
vi.mock("@stripe/react-stripe-js", () => ({ Elements: ({ children }: { children: unknown }) => children }));
// Renders checkout without loading Stripe or making a real payment-intent request.
vi.mock("../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/StripePaymentForm", async () => {
  const { forwardRef, useImperativeHandle } = await import("react");
  return { default: forwardRef((_props, ref) => {
    useImperativeHandle(ref, () => ({ submitPayment: mocks.payment }));
    return <div>Mock payment form</div>;
  }) };
});
// Provides the original imperative payment handle so blocked placement can be checked without invoking Stripe.

let status: string;
let menu: { id: string; status: string }[];
let fetchMock: ReturnType<typeof vi.fn>;
const pricing = { subtotal: 10, deliveryFee: 4.99, serviceFee: 1.2, smallOrderSurcharge: 2.5, taxableSubtotal: 18.69, gst: .93, qst: 1.86, total: 21.48 };
const json = (data: unknown) => new Response(JSON.stringify(data));

beforeEach(() => {
  status = "OPEN";
  menu = [{ id: "item-1", status: "AVAILABLE" }];
  mocks.cart = { restaurantId: "restaurant-1", items: [{ menuItem: { id: "item-1", name: "Soup", status: "AVAILABLE", price: 10, description: null }, quantity: 1 }] };
  mocks.payment.mockReset();
  mocks.clearCart.mockReset();
  vi.stubGlobal("alert", vi.fn());
  fetchMock = vi.fn(async (url: string) => {
    if (url.endsWith("/api/restaurants")) return json(status === "MISSING" ? [] : [{ id: "restaurant-1", status }]);
    if (url.endsWith("/menu")) return json(menu);
    if (url.endsWith("/breakdown")) return json(pricing);
    if (url.endsWith("/create-payment-intent")) return json({ clientSecret: "private-secret" });
    if (url.endsWith("/placeOrder")) return json({ id: "order-1" });
    throw new Error(`Unexpected URL: ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
});
// Resets fixtures and stubs backend responses so each disabled-button test starts independently.

function mount() { return render(<MemoryRouter><CartPage /><OutputConsole /></MemoryRouter>); }
const checkout = () => screen.getByRole("button", { name: "Continue to Checkout" }) as HTMLButtonElement;
const requestsTo = (path: string) => fetchMock.mock.calls.filter(([url]) => url.endsWith(path));
// Renders the actual cart/console and lets assertions inspect visible controls and attempted requests.
async function enterCheckout() {
  await waitFor(() => expect(checkout().disabled).toBe(false));
  fireEvent.click(checkout());
  await screen.findByRole("button", { name: "Pay and Place Order" });
}
// Opens the existing checkout screen so tests can inspect Place Order without submitting it.

describe("Place Order availability (R-DASH-04)", () => {
  it("leaves Continue to Checkout enabled while availability is being checked", async () => {
    let finish!: (response: Response) => void;
    fetchMock.mockImplementationOnce(() => new Promise<Response>((resolve) => { finish = resolve; }));
    mount();
    expect(checkout().disabled).toBe(false);
    await enterCheckout();
    expect((screen.getByRole("button", { name: "Pay and Place Order" }) as HTMLButtonElement).disabled).toBe(true);
    await act(async () => finish(json([{ id: "restaurant-1", status: "OPEN" }])));
    await waitFor(() => expect((screen.getByRole("button", { name: "Pay and Place Order" }) as HTMLButtonElement).disabled).toBe(false));
  });
  // Checks only Place Order waits for availability; Continue to Checkout keeps its existing behavior.

  it.each(["PAUSED", "CLOSED", "MISSING"])("disables Place Order for a %s restaurant without blocking checkout", async (state) => {
    status = state;
    mount();
    await screen.findByText("This restaurant is not accepting orders.");
    expect(checkout().disabled).toBe(false);
    await enterCheckout();
    expect((screen.getByRole("button", { name: "Pay and Place Order" }) as HTMLButtonElement).disabled).toBe(true);
    expect(requestsTo("/placeOrder")).toHaveLength(0);
  });
  // Covers paused, closed, and absent restaurants without adding an early checkout restriction.

  it("disables Place Order when every selected item is sold out", async () => {
    menu = [{ id: "item-1", status: "SOLD_OUT" }];
    mount();
    await screen.findByText("All selected items are sold out or unavailable.");
    expect(checkout().disabled).toBe(false);
    await enterCheckout();
    expect((screen.getByRole("button", { name: "Pay and Place Order" }) as HTMLButtonElement).disabled).toBe(true);
  });
  // Verifies every selected item being sold out disables Place Order and shows the explanation.

  it("does not impose an additional disabled rule on a mixed cart", async () => {
    mocks.cart.items.push({ ...mocks.cart.items[0], menuItem: { ...mocks.cart.items[0].menuItem, id: "item-2", name: "Bread" } });
    menu = [{ id: "item-1", status: "AVAILABLE" }, { id: "item-2", status: "SOLD_OUT" }];
    mount();
    await enterCheckout();
    await waitFor(() => expect((screen.getByRole("button", { name: "Pay and Place Order" }) as HTMLButtonElement).disabled).toBe(false));
    expect(screen.queryByText("Remove the unavailable items before placing your order.")).toBeNull();
  });
  // Guards against introducing an extra mixed-cart restriction beyond the ticket's all-sold-out rule.

  it("keeps Place Order disabled after a failed check and supports retry", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("offline"));
    mount();
    await screen.findByText("Could not check availability. Please retry.");
    await enterCheckout();
    expect((screen.getByRole("button", { name: "Pay and Place Order" }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Retry availability" }));
    await waitFor(() => expect((screen.getByRole("button", { name: "Pay and Place Order" }) as HTMLButtonElement).disabled).toBe(false));
    expect(screen.getByText(/GET \/api\/restaurants failed/)).toBeTruthy();
  });
  // Checks a failed lookup disables placement, logs the failure, and can recover through Retry availability.

  it("refreshes on focus and disables a previously enabled Place Order", async () => {
    mount();
    await enterCheckout();
    await waitFor(() => expect((screen.getByRole("button", { name: "Pay and Place Order" }) as HTMLButtonElement).disabled).toBe(false));
    status = "MISSING";
    fireEvent(window, new Event("focus"));
    await screen.findByText("This restaurant is not accepting orders.");
    expect((screen.getByRole("button", { name: "Pay and Place Order" }) as HTMLButtonElement).disabled).toBe(true);
  });
  // Verifies returning to the window updates a previously enabled button when the restaurant becomes unavailable.

  it("rechecks before placement and stops if every selected item just sold out", async () => {
    mount();
    await enterCheckout();
    const place = screen.getByRole("button", { name: "Pay and Place Order" }) as HTMLButtonElement;
    await waitFor(() => expect(place.disabled).toBe(false));
    menu = [{ id: "item-1", status: "SOLD_OUT" }];
    fireEvent.click(place);
    await screen.findByText("All selected items are sold out or unavailable.");
    expect(mocks.payment).not.toHaveBeenCalled();
    expect(requestsTo("/placeOrder")).toHaveLength(0);
  });
  // Confirms the final recheck stops both placement and the existing payment call when all items just sold out.
});
