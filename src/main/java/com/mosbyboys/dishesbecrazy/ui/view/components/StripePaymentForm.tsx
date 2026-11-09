import { log, logError } from "../outputLog";
import { PaymentElement, useStripe, useElements } from "@stripe/react-stripe-js";
import { type FormEvent, forwardRef, useImperativeHandle, useState } from "react";

export type StripePaymentFormHandle = {
    submitPayment: () => Promise<string>;
};
type Props = { clientSecret: string; onSubmitCheckout: () => void };

const StripePaymentForm = forwardRef<StripePaymentFormHandle, Props>(({ clientSecret, onSubmitCheckout }, ref) => {
    const stripe = useStripe();
    const elements = useElements();
    const [error, setError] = useState("");

    async function submitPayment(): Promise<string> {
        log("command", "Confirm payment");
        setError("");
        try {
            if (!stripe || !elements) throw new Error("Payment form is not ready.");
            const current = await stripe.retrievePaymentIntent(clientSecret);
            if (current.error) throw new Error("Payment status could not be checked. Retry this checkout.");
            if (current.paymentIntent?.status === "requires_capture" || current.paymentIntent?.status === "succeeded") {
                return current.paymentIntent.id;
            }
            // A previously authorized/captured attempt is completed by the server without submitting payment again.

            const result = await stripe.confirmPayment({
                elements,
                confirmParams: { return_url: `${window.location.origin}/cart` },
                redirect: "if_required",
            });
            if (result.error || !result.paymentIntent
                || !["requires_capture", "succeeded"].includes(result.paymentIntent.status)) {
                throw new Error("Payment was not authorized. Check the form and try again.");
            }
            return result.paymentIntent.id;
        } catch (failure) {
            logError(failure, "Payment confirmation failed.");
            setError("Payment could not be authorized. Check the form and try again.");
            throw failure;
        }
    }
    // The parent awaits a real SDK result; errors propagate before any order-completion request.

    useImperativeHandle(ref, () => ({ submitPayment }));

    function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        onSubmitCheckout();
    }
    // Enter in the payment form follows the same availability/payment/order flow as the main button.

    return (
        <form onSubmit={handleSubmit}>
            <PaymentElement />
            {error && <p className="page__status page__status--error" role="alert">{error}</p>}
        </form>
    );
});

export default StripePaymentForm;
