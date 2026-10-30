import {type FormEvent, useRef, useState} from "react";
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

const stripePromise = loadStripe(
    import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY
);

export default function CartPage() {
    const navigate = useNavigate();
    const {
        cart,
        itemCount,
        subtotal,
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

    const isEmpty = !cart || cart.items.length === 0;

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
        //call the stripe payment form to submit
        paymentFormRef.current?.submitPayment();

        //rest of placing order
        if (!cart || !accessToken) return;

        setPlacingOrder(true);
        setOrderError("");

        try {
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
        } catch (error) {
            const message = error instanceof Error ? error.message : "Something went wrong.";
            log("error", `POST /placeOrder: ${message}`);
            setOrderError(message);
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
                                            <button
                                                type="button"
                                                className="cart-qty__btn"
                                                aria-label={`Decrease ${item.menuItem.name}`}
                                                onClick={() =>
                                                    updateQuantity(item.menuItem.id, item.quantity - 1)
                                                }
                                                disabled={!checkout}
                                            >
                                                −
                                            </button>
                                            <span className="cart-qty__value" aria-live="polite">
                                                {item.quantity}
                                            </span>
                                            <button
                                                type="button"
                                                className="cart-qty__btn"
                                                aria-label={`Increase ${item.menuItem.name}`}
                                                onClick={() =>
                                                    updateQuantity(item.menuItem.id, item.quantity + 1)
                                                }
                                                disabled={!checkout}
                                            >
                                                +
                                            </button>
                                        </div>

                                        <div className="list-card__side cart-item__side">
                                            <span className="list-card__price">
                                                ${(item.menuItem.price * item.quantity).toFixed(2)}
                                            </span>
                                            <Button
                                                variant="link"
                                                onClick={() => removeItem(item.menuItem.id)}
                                                disabled={!checkout}
                                            >
                                                Remove
                                            </Button>
                                        </div>
                                    </div>
                                </li>
                            ))}
                        </ul>

                        <section className="cart-summary" aria-label="Order summary">
                            <div className="cart-summary__row">
                                <span className="caption meta">Subtotal</span>
                                <span className="cart-summary__amount">${subtotal.toFixed(2)}</span>
                            </div>

                            {orderError && (
                                <p className="page__status page__status--error" role="alert">
                                    Error: {orderError}
                                </p>
                            )}

                            <div className="cart-summary__actions">
                                <Button variant="secondary" onClick={clearCart} disabled={placingOrder && !checkout}>
                                    Clear cart
                                </Button>
                                <Button onClick={proceedToCheckout} disabled={placingOrder && !checkout}>
                                    Continue to Checkout
                                </Button>
                            </div>
                        </section>

                        {checkout && (
                            <section className="cart-summary" aria-label="Checkout">
                                <Elements
                                    stripe={stripePromise}
                                    options={{ clientSecret: clientSecret }}
                                >
                                    <StripePaymentForm ref={paymentFormRef} />
                                </Elements>

                                <div className="cart-summary__actions">
                                    <Button variant="secondary" onClick={() => {
                                        setCheckout(false);
                                        setClientSecret(undefined);
                                    }} disabled={placingOrder}>
                                        Back to Cart
                                    </Button>
                                    <Button onClick={placeOrder} disabled={placingOrder}>
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
