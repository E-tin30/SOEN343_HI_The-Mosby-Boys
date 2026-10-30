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

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    useImperativeHandle(ref, () => ({
        submitPayment: () => formRef.current?.requestSubmit()
    }));

    async function handleSubmit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault();

        if (!stripe || !elements) {
            return;
        }

        setLoading(true);
        setError("");

        const { error } = await stripe.confirmPayment({
            elements,
            confirmParams: {
                return_url:
                    `${window.location.origin}/payment-success`
            }
        });

        if (error) {
            setError(error.message ?? "Payment failed.");
        }

        setLoading(false);
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