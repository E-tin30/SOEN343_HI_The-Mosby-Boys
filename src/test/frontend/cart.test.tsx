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
  localStorage.clear();
  mocks.payment.mockReset().mockResolvedValue("pi_test");
  mocks.clearCart.mockReset();
  vi.stubGlobal("alert", vi.fn());
  fetchMock = vi.fn(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/api/restaurants")) return json(status === "MISSING" ? [] : [{ id: "restaurant-1", status }]);
    if (url.endsWith("/menu")) return json(menu);
    if (url.endsWith("/breakdown")) return json(pricing);
    if (url.endsWith("/create-payment-intent")) return json({ checkoutId: JSON.parse(options!.body as string).checkoutId, clientSecret: "private-secret", orderId: null, totalMinor: 1200 });
    if (url.endsWith("/placeOrder")) return json({ id: JSON.parse(options!.body as string).checkoutId });
    if (url.endsWith("/cancel")) return json({ orderId: null });
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

it("waits for authorization before creating the order", async () => {
  let authorize!: (id: string) => void;
  mocks.payment.mockImplementation(() => new Promise((resolve) => { authorize = resolve; }));
  renderCart();
  await enterCheckout();
  await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(false));
  fireEvent.click(getPlaceOrderButton());
  await waitFor(() => expect(mocks.payment).toHaveBeenCalledOnce());
  expect(screen.getByText("Payment total: $12.00 CAD")).toBeTruthy();
  expect(getRequestsTo("/placeOrder")).toHaveLength(0);
  expect(mocks.clearCart).not.toHaveBeenCalled();
  await act(async () => { authorize("pi_authorized"); });
  await waitFor(() => expect(getRequestsTo("/placeOrder")).toHaveLength(1));
  await waitFor(() => expect(mocks.clearCart).toHaveBeenCalledOnce());
});

it("a declined payment never creates an order or clears the cart", async () => {
  mocks.payment.mockRejectedValue(new Error("Card declined"));
  renderCart();
  await enterCheckout();
  await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(false));
  fireEvent.click(getPlaceOrderButton());
  await screen.findByText(/Checkout is not confirmed/);
  expect(getRequestsTo("/placeOrder")).toHaveLength(0);
  expect(mocks.clearCart).not.toHaveBeenCalled();
});

it("retries a lost order response using the same checkout", async () => {
  const normal = fetchMock.getMockImplementation()!;
  let completions = 0;
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/placeOrder") && ++completions === 1) throw new TypeError("Lost response");
    return normal(url, options);
  });
  renderCart();
  await enterCheckout();
  await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(false));
  fireEvent.click(getPlaceOrderButton());
  await screen.findByText(/Checkout is not confirmed/);
  expect(mocks.clearCart).not.toHaveBeenCalled();
  await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(false));
  fireEvent.click(getPlaceOrderButton());
  await waitFor(() => expect(mocks.clearCart).toHaveBeenCalledOnce());
  const completionsSent = getRequestsTo("/placeOrder").map(([, options]) => JSON.parse(options.body).checkoutId);
  expect(completionsSent).toHaveLength(2);
  expect(completionsSent[0]).toBe(completionsSent[1]);
  expect(getRequestsTo("/create-payment-intent")).toHaveLength(1);
});

it("retains the attempt ID after payment setup loses its response", async () => {
  const normal = fetchMock.getMockImplementation()!;
  let attempts = 0;
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/create-payment-intent") && ++attempts === 1) throw new TypeError("Lost setup response");
    return normal(url, options);
  });
  renderCart();
  fireEvent.click(getCheckoutButton());
  await screen.findByText(/Could not start checkout/);
  await enterCheckout();
  const ids = getRequestsTo("/create-payment-intent").map(([, options]) => JSON.parse(options.body).checkoutId);
  expect(ids).toHaveLength(2);
  expect(ids[0]).toBe(ids[1]);
});

it("a double click cannot start two checkout requests", async () => {
  renderCart();
  fireEvent.click(getCheckoutButton());
  fireEvent.click(getCheckoutButton());
  await screen.findByRole("button", { name: "Pay and Place Order" });
  expect(getRequestsTo("/create-payment-intent")).toHaveLength(1);
});

it("Back to Cart requires a confirmed cancellation before editing again", async () => {
  const normal = fetchMock.getMockImplementation()!;
  let release!: (response: Response) => void;
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/cancel")) return new Promise<Response>((resolve) => { release = resolve; });
    return normal(url, options);
  });
  renderCart();
  await enterCheckout();
  fireEvent.click(screen.getByRole("button", { name: "Back to Cart" }));
  await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url.endsWith("/cancel"))).toBe(true));
  expect(screen.queryByRole("button", { name: "Continue to Checkout" })).toBeNull();
  await act(async () => { release(json({ orderId: null })); });
  await screen.findByRole("button", { name: "Continue to Checkout" });
  expect(mocks.clearCart).not.toHaveBeenCalled();
});

it("a lost cancellation response cannot discard the existing checkout", async () => {
  const normal = fetchMock.getMockImplementation()!;
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/cancel")) throw new TypeError("Lost cancellation response");
    return normal(url, options);
  });
  renderCart();
  await enterCheckout();
  const record = localStorage.getItem("dishes.checkout.customer-1");
  fireEvent.click(screen.getByRole("button", { name: "Back to Cart" }));
  await screen.findByText(/Cancellation is not confirmed/);
  expect(screen.queryByRole("button", { name: "Continue to Checkout" })).toBeNull();
  expect(localStorage.getItem("dishes.checkout.customer-1")).toBe(record);
});

it("Back to Cart resolves an already-paid checkout to its order", async () => {
  const normal = fetchMock.getMockImplementation()!;
  fetchMock.mockImplementation(async (url: string, options?: RequestInit) => {
    if (url.endsWith("/cancel")) {
      const id = JSON.parse(localStorage.getItem("dishes.checkout.customer-1")!).id;
      return json({ orderId: id });
    }
    return normal(url, options);
  });
  renderCart();
  await enterCheckout();
  fireEvent.click(screen.getByRole("button", { name: "Back to Cart" }));
  await waitFor(() => expect(mocks.clearCart).toHaveBeenCalledOnce());
  expect(getRequestsTo("/placeOrder")).toHaveLength(0);
  expect(mocks.payment).not.toHaveBeenCalled();
});

it("an old payment response cannot clear a different cart", async () => {
  let authorize!: (id: string) => void;
  mocks.payment.mockImplementation(() => new Promise((resolve) => { authorize = resolve; }));
  const view = renderCart();
  await enterCheckout();
  await waitFor(() => expect(getPlaceOrderButton().disabled).toBe(false));
  fireEvent.click(getPlaceOrderButton());
  await waitFor(() => expect(mocks.payment).toHaveBeenCalledOnce());
  cart = createCart([{ menuItem: createMenuItem(), quantity: 2 }]);
  view.rerender(<MemoryRouter><CartPage /><OutputConsole /></MemoryRouter>);
  await act(async () => { authorize("pi_authorized"); });
  expect(mocks.clearCart).not.toHaveBeenCalled();
  expect(getRequestsTo("/placeOrder")).toHaveLength(0);
});
