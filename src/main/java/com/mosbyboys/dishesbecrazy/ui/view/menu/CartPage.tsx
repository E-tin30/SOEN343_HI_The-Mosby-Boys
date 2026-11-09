import { startCheckout, completeCheckout, cancelCheckout, forgetCheckout } from "./api/checkoutApi";
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
import type { Cart } from "./types/cart";

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
    const checkoutRequestActive = useRef(false);
    const checkoutScope = useRef<{ cart: Cart; token: string } | null>(null);
    const currentSelection = useRef({ cart, token: accessToken });
    currentSelection.current = { cart, token: accessToken };
    const mounted = useRef(true);
    useEffect(() => {
        mounted.current = true;
        return () => { mounted.current = false; };
    }, []);
    // Late responses can finish the original checkout without clearing a newer cart or session.


    const [placingOrder, setPlacingOrder] = useState(false);
    const [orderError, setOrderError] = useState("");
    const [checkout, setCheckout] = useState(false);
    const [clientSecret, setClientSecret] = useState<string>();
    const [checkoutId, setCheckoutId] = useState<string>();
    const [checkoutTotal, setCheckoutTotal] = useState<number>();
    const [pricing, setPricing] = useState<PricingBreakdown | null>(null);
    const [pricingError, setPricingError] = useState("");

    const checkoutMatchesSelection = checkoutScope.current?.cart === cart && checkoutScope.current?.token === accessToken;
    useEffect(() => {
        if (checkoutScope.current && !checkoutMatchesSelection) {
            checkoutScope.current = null;
            setCheckout(false);
            setClientSecret(undefined);
            setCheckoutId(undefined);
            setCheckoutTotal(undefined);
        }
    }, [cart, accessToken, checkoutMatchesSelection]);
    // The original attempt remains recoverable, but its payment form cannot be reused for another cart or session.

    const isEmpty = !cart || cart.items.length === 0;
    const availability = useOrderAvailability(cart, accessToken);
    const isOrderDisabled = !availability.allowed || placingOrder || isEmpty || (checkout && !checkoutMatchesSelection);
    const canRetryAvailability = !availability.allowed && !availability.checking;
    // Applies availability to Place Order and offers retry only after a check has finished.

    useEffect(() => {
        if (!cart || cart.items.length === 0 || !accessToken) {
            setPricing(null);
            return;
        }

        setPricing(null);
        setPricingError("");
        // Clears the previous pricing display before loading the current selection.
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
                            restaurantId: cart.restaurantId,
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
                // Routes pricing request and JSON errors through the existing application console.
                if (controller.signal.aborted) return;
                // Prevents an obsolete pricing response from updating the current cart display.
                setPricing(data);
                setPricingError("");
            } catch (error) {
                if (!controller.signal.aborted) {
                    logError(error, "Could not load the pricing breakdown.");
                    setPricingError("Could not load the pricing breakdown.");
                }
                // Shows/logs genuine pricing failures while ignoring deliberate request cancellation.
            }
        };

        fetchPricing();

        return () => controller.abort();

    }, [cart, accessToken]);

    const isCurrentSelection = () => mounted.current
        && currentSelection.current.cart === cart && currentSelection.current.token === accessToken;

    const showCompletedOrder = (orderId: string, attemptId: string) => {
        if (!user || !isCurrentSelection()) return;
        forgetCheckout(user.id, attemptId);
        clearCart();
        alert("Order placed successfully!");
        navigate(`/order-summary/${orderId}`);
    };
    // Only a verified backend confirmation can clear the cart and show success.

    const proceedToCheckout = async () => {
        if (!cart || !accessToken || !user || checkoutRequestActive.current) return;
        checkoutRequestActive.current = true;
        setPlacingOrder(true);
        setOrderError("");
        log("command", "Continue to checkout");
        try {
            const data = await startCheckout(cart, accessToken, user.id);
            if (!isCurrentSelection()) return;
            if (data.orderId) {
                showCompletedOrder(data.orderId, data.checkoutId);
                return;
            }
            setCheckoutId(data.checkoutId);
            checkoutScope.current = { cart, token: accessToken };
            setCheckoutTotal(data.totalMinor);
            setClientSecret(data.clientSecret!);
            setCheckout(true);
        } catch (error) {
            logError(error, "Could not start checkout.");
            if (isCurrentSelection()) setOrderError("Could not start checkout. Refresh the menu and retry this checkout.");
        } finally {
            checkoutRequestActive.current = false;
            if (mounted.current) setPlacingOrder(false);
        }
    };
    // Reuses the stored checkout ID after an interrupted request instead of creating a second payment attempt.

    const handleRetryAvailability = () => {
        log("command", "Refresh order availability");
        void availability.refreshAvailability();
    };

    const placeOrder = async () => {
        if (!cart || !accessToken || !checkoutId || isOrderDisabled || checkoutRequestActive.current) return;
        checkoutRequestActive.current = true;
        setPlacingOrder(true);
        setOrderError("");
        log("command", "Pay and place order");
        try {
            if (!await availability.refreshAvailability() || !isCurrentSelection()) return;
            if (!paymentFormRef.current) throw new Error("Payment form is not ready.");
            const paymentId = await paymentFormRef.current.submitPayment();
            if (typeof paymentId !== "string" || !paymentId.startsWith("pi_")) {
                throw new Error("Payment authorization could not be confirmed.");
            }
            if (!isCurrentSelection()) return;
            const orderId = await completeCheckout(checkoutId, accessToken);
            showCompletedOrder(orderId, checkoutId);
        } catch (error) {
            logError(error, "Could not complete checkout.");
            if (isCurrentSelection()) {
                setOrderError("Checkout is not confirmed. Retry this checkout or use Back to Cart to resolve it safely.");
                void availability.refreshAvailability();
            }
        } finally {
            checkoutRequestActive.current = false;
            if (mounted.current) setPlacingOrder(false);
        }
    };
    // A declined/not-ready payment stops before /placeOrder; an uncertain response retains the same checkout for recovery.

    const backToCart = async () => {
        if (!accessToken || !checkoutId || !user || !checkoutMatchesSelection || checkoutRequestActive.current) return;
        checkoutRequestActive.current = true;
        setPlacingOrder(true);
        setOrderError("");
        log("command", "Back to cart");
        try {
            const orderId = await cancelCheckout(checkoutId, accessToken);
            if (!isCurrentSelection()) return;
            if (orderId) {
                showCompletedOrder(orderId, checkoutId);
                return;
            }
            forgetCheckout(user.id, checkoutId);
            setCheckout(false);
            setClientSecret(undefined);
            setCheckoutId(undefined);
            setCheckoutTotal(undefined);
            checkoutScope.current = null;
        } catch (error) {
            logError(error, "Could not resolve checkout.");
            if (isCurrentSelection()) setOrderError("Cancellation is not confirmed. Retry Back to Cart before starting another payment.");
        } finally {
            checkoutRequestActive.current = false;
            if (mounted.current) setPlacingOrder(false);
        }
    };
    // A timeout cannot discard an authorized payment; completed checkouts resolve to their existing order.

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
                            {canRetryAvailability && (
                                <Button
                                    variant="secondary"
                                    onClick={handleRetryAvailability}
                                    disabled={placingOrder}
                                >
                                    Retry availability
                                </Button>
                            )}
                            {/* Lets the customer rerun a completed blocked/failed check and logs that command. */}

                            {orderError && (
                                <p className="page__status page__status--error" role="alert">
                                    Error: {orderError}
                                </p>
                            )}

                            {!checkout && <div className="cart-summary__actions">
                                <Button variant="secondary" onClick={clearCart} disabled={placingOrder}>
                                    Clear cart
                                </Button>
                                <Button onClick={proceedToCheckout} disabled={placingOrder}>
                                    Continue to Checkout
                                </Button>
                            </div>}
                        </section>

                        {checkout && (
                            <section className="cart-summary" aria-label="Checkout">
                                <p>Payment total: ${((checkoutTotal ?? 0) / 100).toFixed(2)} CAD</p>
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
                                    <StripePaymentForm ref={paymentFormRef} clientSecret={clientSecret!} onSubmitCheckout={() => { void placeOrder(); }}/>
                                </Elements>

                                <div className="cart-summary__actions">
                                    <Button variant="secondary" onClick={() => { void backToCart(); }} disabled={placingOrder}>
                                        Back to Cart
                                    </Button>
                                    <Button onClick={placeOrder} disabled={isOrderDisabled} aria-describedby={availability.reason ? "order-availability" : undefined}>
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
