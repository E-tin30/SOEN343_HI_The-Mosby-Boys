import { useOrderAvailability } from "./useOrderAvailability";
import { loggedFetch, readJson } from "../api/loggedFetch";
import {useEffect, useRef, useState} from "react";
import {useNavigate} from "react-router-dom";
import {useCart} from "../CartContext";
import {useAuth} from "../AuthContext";
import {log, logError} from "../outputLog";
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
    const [clientSecret, setClientSecret] = useState<string>();
    const [pricing, setPricing] = useState<PricingBreakdown | null>(null);
    const [pricingError, setPricingError] = useState("");

    const isEmpty = !cart || cart.items.length === 0;
    const availability = useOrderAvailability(cart, accessToken);
    const submitting = useRef(false);
    const [startingCheckout, setStartingCheckout] = useState(false);
    const orderDisabled = !availability.allowed || placingOrder || startingCheckout || isEmpty;

    useEffect(() => {
        if (!cart || cart.items.length === 0 || !accessToken) {
            setPricing(null);
            return;
        }

        setPricing(null);
        setPricingError("");
        const controller = new AbortController();

        const fetchPricing = async () => {
            try {
                const response = await loggedFetch(
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

                const data = await readJson<PricingBreakdown>(response);
                if (controller.signal.aborted) return;
                setPricing(data);
                setPricingError("");
            } catch (error) {
                if (!controller.signal.aborted) {
                    logError(error, "Could not load the pricing breakdown.");
                    setPricingError("Could not load the pricing breakdown.");
                }
            }
        };

        fetchPricing();

        return () => controller.abort();

    }, [cart, accessToken]);

    const proceedToCheckout = async () => {
        if (!cart || !accessToken || submitting.current || orderDisabled || !pricing) return;
        submitting.current = true;
        setStartingCheckout(true);
        setOrderError("");
        log("command", "Continue to checkout");
        try {
            if (!await availability.refresh()) return;
            const response = await loggedFetch("http://localhost:8080/payments/create-payment-intent", {
                method: "POST",
                headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
                body: JSON.stringify({
                    userId: user?.id,
                    restaurantId: cart.restaurantId,
                    items: cart.items.map((item) => ({ menuItemId: item.menuItem.id, quantity: item.quantity, price: item.menuItem.price })),
                }),
            });
            const data = await readJson<{ clientSecret: string }>(response);
            if (!data.clientSecret) throw new Error("Payment setup did not return a client secret.");
            setClientSecret(data.clientSecret);
            setCheckout(true);
        } catch (error) {
            logError(error, "Could not start checkout.");
            setOrderError("Could not start checkout. Please try again.");
        } finally {
            submitting.current = false;
            setStartingCheckout(false);
        }
    };

    const placeOrder = async () => {
        if (!cart || !accessToken || submitting.current || orderDisabled) return;

        submitting.current = true;
        setPlacingOrder(true);
        setOrderError("");

        log("command", "Pay and place order");
        try {
            if (!await availability.refresh()) return;
            if (!paymentFormRef.current || !await paymentFormRef.current.submitPayment()) return;

            const response = await loggedFetch("http://localhost:8080/placeOrder", {
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

            const order = await readJson<{ id: string }>(response);
            clearCart();
            alert("Order placed successfully!");
            navigate(`/order-summary/${order.id}`);
        } catch (error) {
            logError(error, "Could not place the order.");
            setOrderError("Could not place the order. Please refresh availability and try again.");
            void availability.refresh();
        } finally {
            submitting.current = false;
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
                                        {availability.unavailableIds.includes(item.menuItem.id) && (
                                            <span className="page__status--error">Sold out or unavailable</span>
                                        )}
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
                                                disabled={checkout || startingCheckout || placingOrder}
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
                                                disabled={checkout || startingCheckout || placingOrder}
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
                                                disabled={checkout || startingCheckout || placingOrder}
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
                            {!availability.allowed && !availability.checking && (
                                <Button variant="secondary" onClick={() => {
                                    log("command", "Refresh order availability");
                                    void availability.refresh();
                                }} disabled={placingOrder || startingCheckout}>Retry availability</Button>
                            )}

                            {orderError && (
                                <p className="page__status page__status--error" role="alert">
                                    Error: {orderError}
                                </p>
                            )}

                            {!checkout && <div className="cart-summary__actions">
                                <Button variant="secondary" onClick={clearCart} disabled={placingOrder || startingCheckout}>
                                    Clear cart
                                </Button>
                                <Button onClick={proceedToCheckout} disabled={orderDisabled || !pricing} aria-describedby={availability.reason ? "order-availability" : undefined}>
                                    {startingCheckout ? "Checking checkout…" : "Continue to Checkout"}
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
                                        log("command", "Back to cart");
                                        setCheckout(false);
                                        setClientSecret(undefined);
                                    }} disabled={placingOrder}>
                                        Back to Cart
                                    </Button>
                                    <Button onClick={placeOrder} disabled={orderDisabled} aria-describedby={availability.reason ? "order-availability" : undefined}>
                                        {placingOrder ? "Placing Order…" : "Pay and Place Order"}
                                    </Button>
                                </div>
                            </section>
                        )}
                    </>
                )}
            </main>
        </div>
    );
}
