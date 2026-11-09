import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import CartPage from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/menu/CartPage";
import type { Cart } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/menu/types/cart";
import type { MenuItem } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/menu/types/menu";
import type { PricingBreakdown } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/orders/types/PricingBreakdown";
import OutputConsole from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/OutputConsole";

const mocks = vi.hoisted(() => ({
  clearCart: vi.fn(),
  payment: vi.fn(),
}));
// Holds spies for existing cart/payment actions without contacting live services.
vi.mock("../../main/java/com/mosbyboys/dishesbecrazy/ui/view/AuthContext", () => ({
  useAuth: () => ({ accessToken: "private-token", user: { id: "customer-1" } }),
}));
// Supplies a fake authenticated customer so these tests exercise Place Order rather than login.
vi.mock("../../main/java/com/mosbyboys/dishesbecrazy/ui/view/CartContext", () => ({
  useCart: () => ({
    cart,
    itemCount: cart.items.reduce((count, item) => count + item.quantity, 0),
    clearCart: mocks.clearCart,
    updateQuantity: vi.fn(),
    removeItem: vi.fn(),
  }),
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
// Mocks the payment handle to detect blocked calls; this does not verify the real form wiring or payment flow.

function createMenuItem(overrides: Partial<MenuItem> = {}): MenuItem {
  return {
    id: "item-1",
    name: "Soup",
    status: "AVAILABLE",
    price: 10,
    description: null,
    ...overrides,
  };
}
// Keeps each test focused on the menu fields that differ from the default available item.

function createCart(items: Cart["items"] = [{ menuItem: createMenuItem(), quantity: 1 }]): Cart {
  return { restaurantId: "restaurant-1", items };
}
// Gives each test its own typed selection instead of repeating the complete cart literal.

let cart: Cart;
let status: string;
let menu: { id: string; status: string }[];
let placementError: Response | Error | undefined;
let fetchMock: ReturnType<typeof vi.fn>;
const pricing: PricingBreakdown = {
  subtotal: 10,
  deliveryFee: 4.99,
  serviceFee: 1.2,
  smallOrderSurcharge: 2.5,
  taxableSubtotal: 18.69,
  gst: 0.93,
  qst: 1.86,
  total: 21.48,
};
const json = (data: unknown) => new Response(JSON.stringify(data));

beforeEach(() => {
  status = "OPEN";
  menu = [{ id: "item-1", status: "AVAILABLE" }];
  cart = createCart();
  placementError = undefined;
  mocks.payment.mockReset();
  mocks.clearCart.mockReset();
  vi.stubGlobal("alert", vi.fn());
  fetchMock = vi.fn(async (url: string) => {
    if (url.endsWith("/api/restaurants")) return json(status === "MISSING" ? [] : [{ id: "restaurant-1", status }]);
    if (url.endsWith("/menu")) return json(menu);
    if (url.endsWith("/breakdown")) return json(pricing);
    if (url.endsWith("/create-payment-intent")) return json({ clientSecret: "private-secret" });
    if (url.endsWith("/placeOrder")) {
      if (placementError instanceof Error) throw placementError;
      return placementError ?? json({ id: "order-1" });
    }
    throw new Error(`Unexpected URL: ${url}`);
  });
  vi.stubGlobal("fetch", fetchMock);
});

// Resets fixtures and stubs backend responses so each disabled-button test starts independently.

function renderCart() {
  return render(<MemoryRouter><CartPage /><OutputConsole /></MemoryRouter>);
}
// Renders the actual cart and output console so assertions observe customer-facing behavior.

function getCheckoutButton(): HTMLButtonElement {
  return screen.getByRole<HTMLButtonElement>("button", { name: "Continue to Checkout" });
}

function getPlaceOrderButton(): HTMLButtonElement {
  return screen.getByRole<HTMLButtonElement>("button", { name: "Pay and Place Order" });
}
// Names the controls shared by the scenarios without repeating queries or type assertions.

const getRequestsTo = (path: string) => fetchMock.mock.calls.filter(([url]) => url.endsWith(path));
// Lets assertions check attempted backend actions without performing them.
async function enterCheckout() {
  await waitFor(() => expect(getCheckoutButton().disabled).toBe(false));
  fireEvent.click(getCheckoutButton());
  await screen.findByRole("button", { name: "Pay and Place Order" });
}
// Opens the existing checkout screen so tests can inspect Place Order without submitting it.

describe("Place Order availability (R-DASH-04)", () => {
  it("leaves Continue to Checkout enabled while availability is being checked", async () => {
    let finish!: (response: Response) => void;
    fetchMock.mockImplementationOnce(() => new Promise<Response>((resolve) => { finish = resolve; }));
    renderCart();
    expect(getCheckoutButton().disabled).toBe(false);
    await enterCheckout();
    expect(getPlaceOrderButton().disabled).toBe(true);
    await act(async () => finish(json([{ id: "restaurant-1", status: "OPEN" }])));
    await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(false));
  });
  // Checks only Place Order waits for availability; Continue to Checkout keeps its existing behavior.

  it.each(["PAUSED", "CLOSED", "MISSING"])("disables Place Order for a %s restaurant without blocking checkout", async (state) => {
    status = state;
    renderCart();
    await screen.findByText("This restaurant is not accepting orders.");
    expect(getCheckoutButton().disabled).toBe(false);
    await enterCheckout();
    expect(getPlaceOrderButton().disabled).toBe(true);
    expect(getRequestsTo("/placeOrder")).toHaveLength(0);
  });
  // Covers paused, closed, and absent restaurants without adding an early checkout restriction.

  it("disables Place Order when every selected item is sold out", async () => {
    menu = [{ id: "item-1", status: "SOLD_OUT" }];
    renderCart();
    await screen.findByText("One or more selected items are sold out or unavailable.");
    expect(getCheckoutButton().disabled).toBe(false);
    await enterCheckout();
    expect(getPlaceOrderButton().disabled).toBe(true);
  });
  // Verifies every selected item being sold out disables Place Order and shows the explanation.

  it("disables Place Order when a mixed cart contains an unavailable item", async () => {
    cart = createCart([
      { menuItem: createMenuItem(), quantity: 1 },
      { menuItem: createMenuItem({ id: "item-2", name: "Bread" }), quantity: 1 },
    ]);
    menu = [{ id: "item-1", status: "AVAILABLE" }, { id: "item-2", status: "SOLD_OUT" }];
    renderCart();
    await enterCheckout();
    await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(true));
    expect(screen.getByText("One or more selected items are sold out or unavailable.")).toBeTruthy();
  });
  // Matches backend validation before a mixed selection can trigger payment.

  it("keeps Place Order disabled after a failed check and supports retry", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("offline"));
    renderCart();
    await screen.findByText("Could not check availability. Please retry.");
    await enterCheckout();
    expect(getPlaceOrderButton().disabled).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Retry availability" }));
    await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(false));
    expect(screen.getByText(/GET \/api\/restaurants failed/)).toBeTruthy();
  });
  // Checks a failed lookup disables placement, logs the failure, and can recover through Retry availability.

  it("refreshes on focus and disables a previously enabled Place Order", async () => {
    renderCart();
    await enterCheckout();
    await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(false));
    status = "MISSING";
    fireEvent(window, new Event("focus"));
    await screen.findByText("This restaurant is not accepting orders.");
    expect(getPlaceOrderButton().disabled).toBe(true);
  });
  // Verifies returning to the window updates a previously enabled button when the restaurant becomes unavailable.

  it("rechecks before placement and stops if every selected item just sold out", async () => {
    renderCart();
    await enterCheckout();
    const placeOrderButton = getPlaceOrderButton();
    await waitFor(() => expect(placeOrderButton.disabled).toBe(false));
    menu = [{ id: "item-1", status: "SOLD_OUT" }];
    fireEvent.click(placeOrderButton);
    await screen.findByText("One or more selected items are sold out or unavailable.");
    expect(mocks.payment).not.toHaveBeenCalled();
    expect(getRequestsTo("/placeOrder")).toHaveLength(0);
  });
  // Confirms the final recheck stops both placement and the existing payment call when all items just sold out.
});

describe("Place Order failure feedback", () => {
  it.each([
    [400, "Restaurant is not accepting orders"],
    [409, "Order is not next in line"],
  ])("shows the backend explanation for an HTTP %s rejection", async (status, message) => {
    placementError = new Response(message, { status, headers: { "Content-Type": "text/plain" } });
    renderCart();
    await enterCheckout();
    await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(false));

    fireEvent.click(getPlaceOrderButton());

    await screen.findByText(`Error: ${message}`);
    expect(mocks.clearCart).not.toHaveBeenCalled();
    expect(window.alert).not.toHaveBeenCalled();
  });
  // Customer feedback retains safe domain explanations already supplied by the shared request helper.

  it("keeps generic feedback for a network failure", async () => {
    placementError = new TypeError("private network diagnostic");
    renderCart();
    await enterCheckout();
    await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(false));

    fireEvent.click(getPlaceOrderButton());

    await screen.findByText("Error: Could not place the order. Please refresh availability and try again.");
    expect(screen.queryByText("private network diagnostic")).toBeNull();
    expect(mocks.clearCart).not.toHaveBeenCalled();
  });
  // Unexpected failures use the existing fallback instead of exposing arbitrary error messages.
});
