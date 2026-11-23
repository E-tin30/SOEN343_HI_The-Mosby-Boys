import { log, logError } from "../outputLog";
import {
    PaymentElement,
    useStripe,
    useElements
} from "@stripe/react-stripe-js";

import {
    forwardRef,
    useImperativeHandle,
    useState
} from "react";

export type StripePaymentFormHandle = {
    submitPayment: () => Promise<void>;
};

const StripePaymentForm = forwardRef<StripePaymentFormHandle>((params, ref) => {
    const stripe = useStripe();
    const elements = useElements();

    const [error, setError] = useState("");

    const submitPayment = async (): Promise<void> => {
        if (!stripe || !elements) {
            throw new Error("Payment form is not ready.");
        }

        setError("");
        log("command", "Confirm payment");

        try {
            const { error, paymentIntent } = await stripe.confirmPayment({
                elements,
                redirect: "if_required",
            });

            if (error) {
                throw new Error(error.message || "Payment failed.");
            }

            if (paymentIntent?.status !== "succeeded") {
                throw new Error("Payment has not been completed.");
            }

        } catch (error) {
            logError(error, "Payment confirmation failed.");

            const message = error instanceof Error
                ? error.message
                : "Payment failed.";

            setError(message);
            throw error;
        }
    };

    useImperativeHandle(ref, () => ({
        submitPayment
    }), [submitPayment]);

    return (
        <div>
            <PaymentElement />

            {error && (
                <p style={{ color: "red" }}>
                    {error}
                </p>
            )}
        </div>
    );
});

export default StripePaymentForm;
