import { log, logError } from "../outputLog";
import {
    PaymentElement,
    useStripe,
    useElements
} from "@stripe/react-stripe-js";

import {type FormEvent, forwardRef, useImperativeHandle, useState} from "react";

export type StripePaymentFormHandle = {
    submitPayment: () => Promise<boolean>;
};

const StripePaymentForm = forwardRef<StripePaymentFormHandle>((params, ref) => {
    const stripe = useStripe();
    const elements = useElements();

    const [error, setError] = useState("");

    async function submitPayment(): Promise<boolean> {
        log("command", "Confirm payment");
        if (!stripe || !elements) {
            log("error", "Payment form is not ready.");
            setError("Payment form is not ready. Please try again.");
            return false;
        }
        setError("");
        try {
            const result = await stripe.confirmPayment({ elements, redirect: "if_required" });
            if (result.error || result.paymentIntent?.status !== "succeeded") {
                log("error", "Payment was not completed.");
                setError("Payment was not completed. Please check your payment details.");
                return false;
            }
            return true;
        } catch (error) {
            logError(error, "Payment confirmation failed.");
            setError("Payment confirmation failed. Please try again.");
            return false;
        }
    }

    useImperativeHandle(ref, () => ({ submitPayment }));

    function handleSubmit(e: FormEvent<HTMLFormElement>) {
        // The parent revalidates availability before invoking submitPayment.
        e.preventDefault();
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