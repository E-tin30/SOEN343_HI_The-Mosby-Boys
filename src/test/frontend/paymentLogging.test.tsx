import { fireEvent, render, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import StripePaymentForm from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/StripePaymentForm";
import { getEntries } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/outputLog";

const payment = vi.hoisted(() => ({ ready: true, confirm: vi.fn() }));
vi.mock("@stripe/react-stripe-js", () => ({
  PaymentElement: () => <div>Payment details</div>,
  useElements: () => payment.ready ? {} : null,
  useStripe: () => payment.ready ? { confirmPayment: payment.confirm } : null,
}));
// Replaces Stripe with controlled responses so logging tests cannot perform real payment operations.
beforeEach(() => { payment.ready = true; payment.confirm.mockReset(); });
// Resets readiness and the SDK mock before each logging scenario.

it.each(["declined", "network"])("logs payment handler commands and %s errors without payment details", async (failure) => {
  if (failure === "declined") payment.confirm.mockResolvedValue({ error: { message: "private payment details" } });
  else payment.confirm.mockRejectedValue(new Error("private payment details"));
  const before = getEntries().length;
  const view = render(<StripePaymentForm />);
  fireEvent.submit(view.container.querySelector("form")!);
  await waitFor(() => expect(getEntries().slice(before).filter((e) => e.kind === "error")).toHaveLength(1));
  const entries = getEntries().slice(before);
  expect(entries.map((e) => e.kind)).toEqual(["command", "error"]);
  expect(entries[0].text).toBe("Confirm payment");
  expect(JSON.stringify(entries)).not.toContain("private payment details");
});
// Direct submission covers handler logging with a mocked SDK; it does not exercise the real imperative handle wiring.

it("logs the existing early return when the payment form is not ready", () => {
  payment.ready = false;
  const before = getEntries().length;
  const view = render(<StripePaymentForm />);
  fireEvent.submit(view.container.querySelector("form")!);
  expect(payment.confirm).not.toHaveBeenCalled();
  expect(getEntries().slice(before).map((e) => e.text)).toEqual(["Confirm payment", "Payment form is not ready."]);
});
// Direct submission checks logging for the existing not-ready return, without claiming button-to-form wiring coverage.
