import { log, logError } from "../outputLog";
import { loggedFetch, readJson } from "../api/loggedFetch";
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
            log("error", "Unable to retrieve your order: missing order or session.");
            // Records this local validation failure even though no request can be sent.
            setError("Unable to retrieve your order.");
            return;
        }

        const controller = new AbortController();

        const fetchOrder = async (): Promise<OrderResponse | null> => {
            try {
                setLoading(true);
                setError(null);

                const response = await loggedFetch(
                    `http://localhost:8080/api/orders/${orderId}`,
                    {
                        headers: {
                            Authorization: `Bearer ${accessToken}`,
                        },
                        signal: controller.signal,
                    }
                );

                const data: OrderResponse = await readJson<OrderResponse>(response);
                // Logs the order lookup and unreadable responses through the shared request helpers.

                if (controller.signal.aborted) return null;
                // Discards a response that belongs to a canceled order-summary load.
                setOrder(data);

                return data;
            } catch (error) {
                if (controller.signal.aborted) {
                    return null;
                }
                // Ignores deliberate cancellation instead of showing it as an order-loading error.

                logError(error, "Unable to load your order.");
                // Covers local load failures without repeating errors already logged by the transport.
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
                const response = await loggedFetch(
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

                const data: PricingBreakdown = await readJson<PricingBreakdown>(response);
                // Gives the summary pricing request the same command/error coverage as other API calls.

                if (!controller.signal.aborted) setPricing(data);
                // Updates pricing only while this summary load is still relevant.
            } catch (error) {
                if (!controller.signal.aborted) {
                    logError(error, "Unable to load the order pricing.");
                    setError("Unable to load the order pricing.");
                }
                // Replaces the former silent catch with visible feedback and application-console logging.
            }
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
                <Button variant="secondary" onClick={() => navigate("/orders/history")}>
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
