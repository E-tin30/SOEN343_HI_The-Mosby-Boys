import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useRef } from "react";
import { beforeEach, expect, it, vi } from "vitest";
import StripePaymentForm, { type StripePaymentFormHandle } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/StripePaymentForm";
import { getEntries } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/outputLog";

const payment = vi.hoisted(() => ({ ready: true, confirm: vi.fn(), retrieve: vi.fn() }));
vi.mock("@stripe/react-stripe-js", () => ({
  PaymentElement: () => <div>Payment details</div>,
  useElements: () => payment.ready ? {} : null,
  useStripe: () => payment.ready ? { confirmPayment: payment.confirm, retrievePaymentIntent: payment.retrieve } : null,
}));
beforeEach(() => {
  payment.ready = true;
  payment.confirm.mockReset();
  payment.retrieve.mockReset().mockResolvedValue({ paymentIntent: { status: "requires_payment_method" } });
});
// SDK calls are mocked; tests click the real imperative handle used by CartPage rather than bypassing its wiring.

function Harness({ result = vi.fn() }: { result?: (id: string) => void }) {
  const ref = useRef<StripePaymentFormHandle>(null);
  const submit = () => { void ref.current!.submitPayment().then(result).catch(() => {}); };
  return <><StripePaymentForm ref={ref} clientSecret="test-client-secret" onSubmitCheckout={submit} />
    <button onClick={submit}>Pay</button></>;
}

it.each(["declined", "network"])("logs payment commands and %s errors without payment details", async (failure) => {
  if (failure === "declined") payment.confirm.mockResolvedValue({ error: { message: "private payment details" } });
  else payment.confirm.mockRejectedValue(new Error("private payment details"));
  const before = getEntries().length;
  render(<Harness />);
  fireEvent.click(screen.getByRole("button", { name: "Pay" }));
  await waitFor(() => expect(getEntries().slice(before).filter((e) => e.kind === "error")).toHaveLength(1));
  const entries = getEntries().slice(before);
  expect(entries.map((e) => e.kind)).toEqual(["command", "error"]);
  expect(entries[0].text).toBe("Confirm payment");
  expect(JSON.stringify(entries)).not.toContain("private payment details");
});

it("stops when the payment form is not ready", async () => {
  payment.ready = false;
  const before = getEntries().length;
  render(<Harness />);
  fireEvent.click(screen.getByRole("button", { name: "Pay" }));
  await waitFor(() => expect(getEntries().slice(before)).toHaveLength(2));
  expect(payment.confirm).not.toHaveBeenCalled();
  expect(getEntries().slice(before).map((e) => e.kind)).toEqual(["command", "error"]);
});

it("returns an authorization only after the real handle's SDK promise resolves", async () => {
  let authorize!: (result: unknown) => void;
  payment.confirm.mockImplementation(() => new Promise((resolve) => { authorize = resolve; }));
  const result = vi.fn();
  render(<Harness result={result} />);
  fireEvent.click(screen.getByRole("button", { name: "Pay" }));
  await waitFor(() => expect(payment.confirm).toHaveBeenCalledOnce());
  expect(result).not.toHaveBeenCalled();
  await act(async () => { authorize({ paymentIntent: { id: "pi_authorized", status: "requires_capture" } }); });
  expect(result).toHaveBeenCalledWith("pi_authorized");
});

it.each(["requires_capture", "succeeded"])("reuses an existing %s payment instead of confirming it again", async (status) => {
  payment.retrieve.mockResolvedValue({ paymentIntent: { id: "pi_existing", status } });
  const result = vi.fn();
  render(<Harness result={result} />);
  fireEvent.click(screen.getByRole("button", { name: "Pay" }));
  await waitFor(() => expect(result).toHaveBeenCalledWith("pi_existing"));
  expect(payment.confirm).not.toHaveBeenCalled();
});

it("Enter submits through the parent's coordinated checkout callback", () => {
  const submit = vi.fn();
  const view = render(<StripePaymentForm clientSecret="test-secret" onSubmitCheckout={submit} />);
  fireEvent.submit(view.container.querySelector("form")!);
  expect(submit).toHaveBeenCalledOnce();
  expect(payment.confirm).not.toHaveBeenCalled();
});
