import { useOrderAvailability } from "./useOrderAvailability";
import {useEffect, useRef, useState} from "react";
import {useNavigate} from "react-router-dom";
import {useCart} from "../CartContext";
import {useAuth} from "../AuthContext";
import {log} from "../outputLog";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import "../styles/CartPage.css";
import {Elements} from "@stripe/react-stripe-js";
import {loadStripe} from "@stripe/stripe-js";
import StripePaymentForm, {StripePaymentFormHandle} from "../components/StripePaymentForm";
import {PricingBreakdown} from "../orders/types/PricingBreakdown";

const stripePromise = loadStripe(
    import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY
);

export default function CartPage() {
    const navigate = useNavigate();
    const {
        cart,
        itemCount,
        updateQuantity,
        removeItem,
        clearCart,
    } = useCart();
    const {accessToken, user} = useAuth();
    const paymentFormRef = useRef<StripePaymentFormHandle>(null);

    const [placingOrder, setPlacingOrder] = useState(false);
    const [orderError, setOrderError] = useState("");
    const [checkout, setCheckout] = useState(false);
    const [clientSecret, setClientSecret] = useState();
    const [pricing, setPricing] = useState<PricingBreakdown | null>(null);
    const [pricingError, setPricingError] = useState("");

    const isEmpty = !cart || cart.items.length === 0;
    const availability = useOrderAvailability(cart, accessToken);
    const orderDisabled = !availability.allowed || placingOrder || isEmpty;
    // Applies current availability to Place Order while retaining the existing empty/pending guards.

    useEffect(() => {
        if (!cart || cart.items.length === 0 || !accessToken) {
            setPricing(null);
            return;
        }

        const controller = new AbortController();

        const fetchPricing = async () => {
            try {
                const response = await fetch(
                    "http://localhost:8080/payments/breakdown",
                    {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json",
                            Authorization: `Bearer ${accessToken}`,
                        },
                        body: JSON.stringify({
                            items: cart.items.map((item) => ({
                                menuItemId: item.menuItem.id,
                                quantity: item.quantity,
                                price: item.menuItem.price,
                            })),
                        }),
                        signal: controller.signal,
                    }
                );

                if (!response.ok) {
                    throw new Error("Failed to retrieve pricing breakdown.");
                }

                const data: PricingBreakdown = await response.json();

                setPricing(data);
                setPricingError("");
            } catch (error) {
                if (error instanceof Error && error.name !== "AbortError") {
                    setPricingError(error.message);
                }
            }
        };

        fetchPricing();

        return () => controller.abort();

    }, [cart, accessToken]);

    const proceedToCheckout = async () => {
        if (!cart || !accessToken) return;

        const response = await fetch(
            "http://localhost:8080/payments/create-payment-intent",
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${accessToken}`,
                },
                body: JSON.stringify({
                    userId: user?.id,
                    restaurantId: cart.restaurantId,
                    items: cart.items.map((item) => ({
                        menuItemId: item.menuItem.id,
                        quantity: item.quantity,
                        price: item.menuItem.price,
                    })),
                }),
            }
        );

        const data = await response.json();

        setClientSecret(data.clientSecret);
        setCheckout(true);
    }

    const placeOrder = async () => {
        if (!cart || !accessToken || orderDisabled) return;
        // Stops placement when the current selection/session is missing or R-DASH-04 blocks the order.

        setPlacingOrder(true);
        setOrderError("");

        try {
            if (!await availability.refresh()) return;
            // Rechecks immediately before placement in case availability changed after rendering.
            paymentFormRef.current?.submitPayment();

            log("command", "POST /placeOrder");
            const response = await fetch("http://localhost:8080/placeOrder", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    Authorization: `Bearer ${accessToken}`,
                },
                body: JSON.stringify({
                    userId: user?.id,
                    restaurantId: cart.restaurantId,
                    items: cart.items.map((item) => ({
                        menuItemId: item.menuItem.id,
                        menuItemName: item.menuItem.name,
                        quantity: item.quantity,
                        price: item.menuItem.price,
                    })),
                }),
            });

            if (!response.ok) {
                throw new Error(await response.text() || "Failed to place order.");
            }

            clearCart();
            alert("Order placed successfully!");

            //wait for backend before redirecting
            const order = await response.json();
            navigate(`/order-summary/${order.id}`);
        } catch (error) {
            const message = error instanceof Error ? error.message : "Something went wrong.";
            log("error", `POST /placeOrder: ${message}`);
            setOrderError(message);
            void availability.refresh();
            // Refreshes the disabled state after a possible backend availability rejection.
        } finally {
            setPlacingOrder(false);
        }
    };

    return (
        <div className="page">
            <AppHeader>
                {cart ? (
                    <Button
                        variant="secondary"
                        onClick={() => navigate(`/restaurants/${cart.restaurantId}/menu`)}
                    >
                        Back to menu
                    </Button>
                ) : (
                    <Button variant="secondary" onClick={() => navigate("/restaurants")}>
                        Back to restaurants
                    </Button>
                )}
            </AppHeader>

            <main className="page__content card">
                <header className="page__intro">
                    <Heading>Your ~cart.~</Heading>
                    <p className="meta">
                        {isEmpty
                            ? "Nothing here yet."
                            : `${itemCount} ${itemCount === 1 ? "item" : "items"} ready to order.`}
                    </p>
                </header>

                {isEmpty ? (
                    <div className="cart-empty">
                        <p className="page__status meta">Your cart is empty.</p>
                        <Button onClick={() => navigate("/restaurants")}>
                            Browse restaurants
                        </Button>
                    </div>
                ) : (
                    <>
                        <ul className="page__list">
                            {cart.items.map((item) => (
                                <li key={item.menuItem.id} className="list-card cart-item">
                                    <div className="list-card__info">
                                        <Heading level={3}>{item.menuItem.name}</Heading>
                                        <span className="meta">
                                            ${item.menuItem.price.toFixed(2)} each
                                        </span>
                                    </div>

                                    <div className="cart-item__controls">
                                        <div className="cart-qty">
                                            {!checkout && <button
                                                type="button"
                                                className="cart-qty__btn"
                                                aria-label={`Decrease ${item.menuItem.name}`}
                                                onClick={() =>
                                                    updateQuantity(item.menuItem.id, item.quantity - 1)
                                                }
                                                disabled={checkout}
                                            >
                                                −
                                            </button>}
                                            <span className="cart-qty__value" aria-live="polite">
                                                {item.quantity}
                                            </span>
                                            {!checkout && <button
                                                type="button"
                                                className="cart-qty__btn"
                                                aria-label={`Increase ${item.menuItem.name}`}
                                                onClick={() =>
                                                    updateQuantity(item.menuItem.id, item.quantity + 1)
                                                }
                                                disabled={checkout}
                                            >
                                                +
                                            </button>}
                                        </div>

                                        <div className="list-card__side cart-item__side">
                                            <span className="list-card__price">
                                                ${(item.menuItem.price * item.quantity).toFixed(2)}
                                            </span>
                                            {!checkout && <Button
                                                variant="link"
                                                onClick={() => removeItem(item.menuItem.id)}
                                                disabled={checkout}
                                            >
                                                Remove
                                            </Button>}
                                        </div>
                                    </div>
                                </li>
                            ))}
                        </ul>

                        <section className="cart-summary" aria-label="Order summary">
                            <Heading level={3}>Order Summary</Heading>

                            {pricing ? (
                                <>
                                    <div className="cart-summary__row">
                                        <span className="caption meta">Subtotal</span>
                                        <span>${pricing.subtotal.toFixed(2)}</span>
                                    </div>

                                    <div className="cart-summary__row">
                                        <span className="caption meta">Delivery Fee</span>
                                        <span>${pricing.deliveryFee.toFixed(2)}</span>
                                    </div>

                                    <div className="cart-summary__row">
                                        <span className="caption meta">Service Fee</span>
                                        <span>${pricing.serviceFee.toFixed(2)}</span>
                                    </div>

                                    {pricing.smallOrderSurcharge > 0 && (
                                        <div className="cart-summary__row">
                                            <span className="caption meta">
                                                Small Order Surcharge
                                            </span>
                                            <span>
                                                ${pricing.smallOrderSurcharge.toFixed(2)}
                                            </span>
                                        </div>
                                    )}

                                    <div className="cart-summary__row">
                                        <span className="caption meta">Taxable Subtotal</span>
                                        <span>${pricing.taxableSubtotal.toFixed(2)}</span>
                                    </div>

                                    <div className="cart-summary__row">
                                        <span className="caption meta">GST</span>
                                        <span>${pricing.gst.toFixed(2)}</span>
                                    </div>

                                    <div className="cart-summary__row">
                                        <span className="caption meta">QST</span>
                                        <span>${pricing.qst.toFixed(2)}</span>
                                    </div>

                                    <div className="cart-summary__row">
                                        <strong>Total</strong>
                                        <strong className="cart-summary__amount">
                                            ${pricing.total.toFixed(2)}
                                        </strong>
                                    </div>
                                </>
                            ) : (
                                <p className="meta">
                                    {pricingError || "Calculating order total..."}
                                </p>
                            )}

                            {availability.reason && (
                                <p id="order-availability" className="page__status" role="status">
                                    {availability.reason}
                                </p>
                            )}
                            {/* Explains the current Place Order restriction and announces updates accessibly. */}
                            {!availability.allowed && !availability.checking && (
                                <Button variant="secondary" onClick={() => {
                                    log("command", "Refresh order availability");
                                    void availability.refresh();
                                }} disabled={placingOrder}>Retry availability</Button>
                            )}
                            {/* Lets the customer rerun a completed blocked/failed check and logs that command. */}

                            {orderError && (
                                <p className="page__status page__status--error" role="alert">
                                    Error: {orderError}
                                </p>
                            )}

                            {!checkout && <div className="cart-summary__actions">
                                <Button variant="secondary" onClick={clearCart} disabled={placingOrder && checkout}>
                                    Clear cart
                                </Button>
                                <Button onClick={proceedToCheckout} disabled={placingOrder && checkout}>
                                    Continue to Checkout
                                </Button>
                            </div>}
                        </section>

                        {checkout && (
                            <section className="cart-summary" aria-label="Checkout">
                                <Elements
                                    stripe={stripePromise}
                                    options={{
                                        clientSecret: clientSecret,
                                        appearance: {
                                            theme: "stripe",
                                            variables: {
                                                colorBackground: "#F6EEDF",
                                                colorText: "#333333",
                                                colorPrimary: "#333333",
                                                borderRadius: "8px",
                                            },
                                            rules: {
                                                ".Input": {
                                                    backgroundColor: "#FBF5EA",
                                                    border: "1px solid #D8D2C2",
                                                },
                                                ".Input:focus": {
                                                    border: "1px solid #333333",
                                                    boxShadow: "none",
                                                },
                                            }
                                        },
                                    }}

                                >
                                    <StripePaymentForm ref={paymentFormRef}/>
                                </Elements>

                                <div className="cart-summary__actions">
                                    <Button variant="secondary" onClick={() => {
                                        setCheckout(false);
                                        setClientSecret(undefined);
                                    }} disabled={placingOrder}>
                                        Back to Cart
                                    </Button>
                                    <Button onClick={placeOrder} disabled={orderDisabled} aria-describedby={availability.reason ? "order-availability" : undefined}>
                                        {placingOrder ? "Placing Order…" : "Pay and Place Order"}
                                    </Button>
                                    {/* Only Place Order receives the availability restriction and its explanatory text. */}
                                </div>
                            </section>
                        )}
                    </>
                )}
            </main>
        </div>
    );
}
