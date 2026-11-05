import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { getHomeRoute } from "../roleRoutes";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import Tag from "../components/Tag";
import { cancelOrder, getOrderHistory, refundOrder } from "./api/ordersApi";
import type { HistoryOrder, HistoryStatus } from "./types/order";
import "../styles/OrderHistoryPage.css";

type Tone = "sage" | "mustard" | "tomato" | "navy" | "neutral";

const STATUS_LABEL: Record<HistoryStatus, string> = {
  PLACED: "Placed",
  ACCEPTED: "Accepted",
  PREPARING: "Preparing",
  READY: "Ready",
  PICKEDUP: "Picked up",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
  REJECTED: "Rejected",
  REFUNDED: "Refunded",
};

const STATUS_TONE: Record<HistoryStatus, Tone> = {
  PLACED: "mustard",
  ACCEPTED: "mustard",
  PREPARING: "mustard",
  READY: "mustard",
  PICKEDUP: "navy",
  DELIVERED: "sage",
  CANCELLED: "tomato",
  REJECTED: "tomato",
  REFUNDED: "navy",
};

const formatDate = (iso: string) =>
  new Date(iso).toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });

export default function OrderHistoryPage() {
  const navigate = useNavigate();
  const { user, accessToken } = useAuth();
  const [orders, setOrders] = useState<HistoryOrder[] | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [error, setError] = useState("");

  const loadOrders = useCallback(async () => {
    if (!accessToken) return;
    try {
      setOrders(await getOrderHistory(accessToken));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't load your orders.");
    }
  }, [accessToken]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  // Runs one action on one order, then reloads so the page matches the backend.
  const runAction = async (orderId: string, call: () => Promise<void>) => {
    setPendingId(orderId);
    setError("");
    try {
      await call();
      await loadOrders();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setPendingId(null);
    }
  };

  const handleCancel = (order: HistoryOrder) => {
    if (!accessToken) return;
    if (!window.confirm("Cancel this order?")) return;
    runAction(order.id, () => cancelOrder(accessToken, order.id));
  };

  const handleRefund = (order: HistoryOrder) => {
    if (!accessToken) return;
    if (!window.confirm("Refund this order?")) return;
    runAction(order.id, () => refundOrder(accessToken, order.id));
  };

  // The backend enforces these rules too; the page only decides which buttons to show.
  const canCancel = (o: HistoryOrder) => user?.role === "CUSTOMER" && o.status === "PLACED";
  const canRefund = (o: HistoryOrder) => user?.role === "OPERATOR" && o.status === "DELIVERED";

  const renderOrder = (order: HistoryOrder) => (
    <li key={order.id} className="list-card history-card">
        <div className="history-card__top">
            <Heading level={3}>{`Order #${order.id.slice(0, 6)}`}</Heading>
            <Tag tone={STATUS_TONE[order.status]}>{STATUS_LABEL[order.status]}</Tag>
        </div>

        <span className="meta">
            {[
                user?.role !== "RESTAURANT" ? order.restaurantName : null,
                order.customerName,
                formatDate(order.placedAt),
            ].filter(Boolean).join(" · ")}
        </span>

        {order.status === "REJECTED" && order.rejectionReason && (
            <p className="history-card__reason">Rejected: {order.rejectionReason}</p>
        )}

        <ul className="history-card__lines">
            {order.lines.map((line) => (
                <li key={line.menuItemId} className="history-card__line">
                    <span>
                        <span className="history-card__qty">{line.quantity}×</span> {line.menuItemName}
                        {line.quantity > 1 && (
                            <span className="meta"> (${line.unitPrice.toFixed(2)} each)</span>
                        )}
                    </span>
                    <span>${(line.unitPrice * line.quantity).toFixed(2)}</span>
                </li>
            ))}
        </ul>

        <div className="history-card__bottom">
            <span className="list-card__price">
                {order.total !== null ? `$${order.total.toFixed(2)}` : "—"}
            </span>
            <div className="history-card__actions">
            {canCancel(order) && (
                <Button variant="link" disabled={pendingId !== null} onClick={() => handleCancel(order)}>
                {pendingId === order.id ? "Saving…" : "Cancel order"}
                </Button>
            )}
            {canRefund(order) && (
                <Button variant="secondary" disabled={pendingId !== null} onClick={() => handleRefund(order)}>
                {pendingId === order.id ? "Saving…" : "Refund"}
                </Button>
            )}
            </div>
        </div>
    </li>
  );

  const current = orders?.filter((o) => o.status !== "REFUNDED") ?? [];
  const refunded = orders?.filter((o) => o.status === "REFUNDED") ?? [];

  return (
    <div className="page">
      <AppHeader>
        <Button variant="secondary" onClick={() => navigate(user ? getHomeRoute(user.role) : "/login")}>
          Back to home
        </Button>
      </AppHeader>

      <main className="history-board">
        <Heading level={2}>Order history</Heading>

        {error && (
          <p className="page__status page__status--error" role="alert">
            Error: {error}
          </p>
        )}

        {!error && orders === null && <p className="page__status meta">Loading…</p>}

        {orders && (
          <>
            <section className="card history-section" aria-label="Orders">
              <header className="history-section__header">
                <Heading level={3}>Orders</Heading>
                <Tag>{String(current.length)}</Tag>
              </header>
              {current.length === 0 ? (
                <p className="page__status meta">No orders yet.</p>
              ) : (
                <ul className="page__list">{current.map(renderOrder)}</ul>
              )}
            </section>

            {refunded.length > 0 && (
              <section className="card history-section" aria-label="Refunded orders">
                <header className="history-section__header">
                  <Heading level={3}>Refunded</Heading>
                  <Tag tone="navy">{String(refunded.length)}</Tag>
                </header>
                <ul className="page__list">{refunded.map(renderOrder)}</ul>
              </section>
            )}
          </>
        )}
      </main>
    </div>
  );
}