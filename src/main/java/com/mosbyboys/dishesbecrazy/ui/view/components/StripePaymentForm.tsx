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

        if (!stripe || !elements) {
            return;
        }

        setError("");

        const { error } = await stripe.confirmPayment({
            elements,
            redirect: "if_required",
        });

        if (error) {
            setError(error.message ?? "Payment failed.");
        }

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