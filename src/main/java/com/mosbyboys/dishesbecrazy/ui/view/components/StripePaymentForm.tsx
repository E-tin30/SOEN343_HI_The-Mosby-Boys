import { log, logError } from "../outputLog";
import {
    PaymentElement,
    useStripe,
    useElements
} from "@stripe/react-stripe-js";

import {type FormEvent, forwardRef, useImperativeHandle, useRef, useState} from "react";

export type StripePaymentFormHandle = {
    submitPayment: () => void;
};

const StripePaymentForm = forwardRef<StripePaymentFormHandle>((params, ref) => {
    const stripe = useStripe();
    const elements = useElements();
    const formRef = useRef<HTMLFormElement>(null);

    const [error, setError] = useState("");

    useImperativeHandle(ref, () => ({
        submitPayment: () => formRef.current?.requestSubmit()
    }));

    async function handleSubmit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault();

        log("command", "Confirm payment");
        // Records the existing SDK action without changing the payment submission contract.
        if (!stripe || !elements) {
            log("error", "Payment form is not ready.");
            // Makes the existing early return visible in the application console.
            return;
        }

        setError("");

        try {
            const { error } = await stripe.confirmPayment({
                elements,
                redirect: "if_required",
            });
            if (error) {
                log("error", "Payment confirmation failed.");
                // Logs a safe description instead of exposing payment details from the SDK error.
                setError(error.message ?? "Payment failed.");
            }
        } catch (error) {
            logError(error, "Payment confirmation failed.");
            setError("Payment failed.");
        }
        // Reports thrown SDK/network failures through the same console as application requests.

    }

    return (
        <form onSubmit={handleSubmit}>
            <PaymentElement />

            {error && (
                <p style={{ color: "red" }}>
                    {error}
                </p>
            )}

        </form>
    );
});

export default StripePaymentForm;