import {useNavigate, useParams} from "react-router-dom";
import {useAuth} from "../AuthContext";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import "../styles/CartPage.css";
import {useEffect, useState} from "react";
import {PricingBreakdown} from "./types/PricingBreakdown";
import {OrderResponse} from "./types/order";

export default function OrderSummaryPage() {
    const navigate = useNavigate();
    const { orderId } = useParams<{ orderId: string }>();
    const { accessToken, user } = useAuth();

    const [order, setOrder] = useState<OrderResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [pricing, setPricing] = useState<PricingBreakdown | null>(null);

    useEffect(() => {
        if (!orderId || !accessToken) {
            setLoading(false);
            setError("Unable to retrieve your order.");
            return;
        }

        const controller = new AbortController();

        const fetchOrder = async (): Promise<OrderResponse | null> => {
            try {
                setLoading(true);
                setError(null);

                const response = await fetch(
                    `http://localhost:8080/api/orders/${orderId}`,
                    {
                        headers: {
                            Authorization: `Bearer ${accessToken}`,
                        },
                        signal: controller.signal,
                    }
                );

                if (!response.ok) {
                    throw new Error("Failed to retrieve order.");
                }

                const data: OrderResponse = await response.json();

                setOrder(data);

                return data;
            } catch (error) {
                if (
                    error instanceof Error &&
                    error.name === "AbortError"
                ) {
                    return null;
                }

                setError("Unable to load your order.");
                return null;
            } finally {
                if (!controller.signal.aborted) {
                    setLoading(false);
                }
            }
        };

        const fetchPricing = async (order: OrderResponse) => {
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
                            items: order.orderLines.map((item) => ({
                                menuItemId: item.menuItemId,
                                quantity: item.quantity,
                                price: item.unitPrice,
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
            } catch (error) {}
        };

        const loadOrder = async () => {
            const order = await fetchOrder();

            if (order) {
                await fetchPricing(order);
            }
        };

        loadOrder();

        return () => controller.abort();
    }, [orderId, accessToken]);


    return (
        <div className="page">
            <AppHeader>
                <Button variant="secondary" onClick={() => navigate("/order-history")}>
                    See Order History
                </Button>
            </AppHeader>

            <main className="page__content card">
                <header className="page__intro">
                    <Heading>Your ~Order~</Heading>
                    <p className="meta">
                        Here is the status of your order{" "}{user?.firstName}!
                    </p>
                </header>

                {loading && (
                    <p className="meta">
                        Loading your order...
                    </p>
                )}

                {error && (
                    <p className="meta" role="alert">
                        {error}
                    </p>
                )}

                {order && (
                    <>
                        <section className="order-status">
                            <Heading level={3}>
                                Order Status
                            </Heading>

                            <p className="summary-state">
                                {order.state.replace("State", "").toUpperCase()}
                            </p>
                        </section>

                        <section>
                            <Heading level={3}>
                                Order Items
                            </Heading>

                            <ul className="page__list">
                                {order.orderLines.map((item) => (
                                    <li
                                        key={item.menuItemId}
                                        className="list-card cart-item"
                                    >
                                        <div className="list-card__info">
                                            <Heading level={3}>
                                                {item.menuItemName}
                                            </Heading>

                                            <span className="meta">
                                                ${item.unitPrice.toFixed(2)} each
                                            </span>
                                        </div>

                                        <div className="cart-item__controls">
                                            <span>
                                                Qty: {item.quantity}
                                            </span>

                                            <span className="list-card__price">
                                                ${
                                                (
                                                    item.unitPrice *
                                                    item.quantity
                                                ).toFixed(2)
                                            }
                                            </span>
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        </section>

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
                                    Calculating order total...
                                </p>
                            )}
                        </section>
                    </>
                )}

                <Button
                    onClick={() => navigate("/home")}
                >
                    Back Home
                </Button>
            </main>
        </div>
    );
}
