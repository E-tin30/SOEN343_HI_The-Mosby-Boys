import { createRef } from "react";
import { act, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import StripePaymentForm, { type StripePaymentFormHandle } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/StripePaymentForm";
import { getEntries } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/outputLog";

const payment = vi.hoisted(() => ({ ready: true, confirm: vi.fn() }));
vi.mock("@stripe/react-stripe-js", () => ({
  PaymentElement: () => <div>Payment details</div>,
  useElements: () => payment.ready ? {} : null,
  useStripe: () => payment.ready ? { confirmPayment: payment.confirm } : null,
}));
beforeEach(() => { payment.ready = true; payment.confirm.mockReset(); });

it("resolves confirmation only after Stripe succeeds", async () => {
  const ref = createRef<StripePaymentFormHandle>();
  let finish!: (value: unknown) => void;
  payment.confirm.mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
  render(<StripePaymentForm ref={ref} />);
  let pending!: Promise<boolean>;
  act(() => { pending = ref.current!.submitPayment(); });
  await act(async () => { finish({ paymentIntent: { status: "succeeded" } }); });
  expect(await pending).toBe(true);
});

it("returns false and logs a safe error when Stripe declines the payment", async () => {
  const ref = createRef<StripePaymentFormHandle>();
  payment.confirm.mockResolvedValue({ error: { message: "private payment details" } });
  const before = getEntries().length;
  render(<StripePaymentForm ref={ref} />);
  let accepted: boolean | undefined;
  await act(async () => { accepted = await ref.current!.submitPayment(); });
  expect(accepted).toBe(false);
  expect(screen.getByText(/Payment was not completed/)).toBeTruthy();
  expect(JSON.stringify(getEntries().slice(before))).not.toContain("private payment details");
});

it("does not confirm before the payment form is ready", async () => {
  payment.ready = false;
  const ref = createRef<StripePaymentFormHandle>();
  render(<StripePaymentForm ref={ref} />);
  await act(async () => { expect(await ref.current!.submitPayment()).toBe(false); });
  expect(payment.confirm).not.toHaveBeenCalled();
});
