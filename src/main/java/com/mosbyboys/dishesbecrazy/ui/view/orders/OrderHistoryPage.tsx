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
import { STATUS_LABEL, STATUS_TONE } from "./orderStatus";
import "../styles/OrderHistoryPage.css";

// Tabs group the states by what the reader does with them; null means every state.
// "Delivered" is the operator's refund queue (R-PRC-05); each card's tag still shows the exact state.
type TabLabel = "All" | "In progress" | "Delivered" | "Refunded" | "Cancelled / rejected";
const TABS: Record<TabLabel, HistoryStatus[] | null> = {
  All: null,
  "In progress": ["PLACED", "ACCEPTED", "PREPARING", "READY", "PICKEDUP"],
  Delivered: ["DELIVERED"],
  Refunded: ["REFUNDED"],
  "Cancelled / rejected": ["CANCELLED", "REJECTED"],
};

const formatDate = (iso: string) =>
  new Date(iso).toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });

const money = (amount: number) => `$${amount.toFixed(2)}`;

const showTotal = (o: HistoryOrder) => {
  const total = o.pricing?.total ?? o.total;
  return total === null ? "—" : money(total);
};

const PriceRow = ({ label, amount }: { label: string; amount: number }) => (
  <div className="history-card__price-row">
    <span className="caption meta">{label}</span>
    <span>{money(amount)}</span>
  </div>
);

export default function OrderHistoryPage() {
  const navigate = useNavigate();
  const { user, accessToken } = useAuth();
  const [orders, setOrders] = useState<HistoryOrder[] | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  // The order whose Refund was clicked; its card asks to confirm inline instead of a browser pop-up.
  const [confirmRefundId, setConfirmRefundId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [tab, setTab] = useState<TabLabel>("All");

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
    setConfirmRefundId(null);
    runAction(order.id, () => refundOrder(accessToken, order.id));
  };

  // The backend enforces these rules too; the page only decides which buttons to show.
  const canCancel = (o: HistoryOrder) => user?.role === "CUSTOMER" && o.status === "PLACED";
  const canRefund = (o: HistoryOrder) => user?.role === "OPERATOR" && o.status === "DELIVERED";

  const renderOrder = (order: HistoryOrder) => (
    <li key={order.id} className="list-card history-card">
        <div className="history-card__top">
            <Heading level={3}>{`Order #${order.id.slice(-6)}`}</Heading>
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

        {order.pricing && (
                            <div className="history-card__pricing">
                                <PriceRow label="Subtotal" amount={order.pricing.subtotal} />
                                <PriceRow label="Delivery fee" amount={order.pricing.deliveryFee} />
                                <PriceRow label="Service fee" amount={order.pricing.serviceFee} />
                                {order.pricing.smallOrderSurcharge > 0 && (
                                    <PriceRow label="Small order surcharge" amount={order.pricing.smallOrderSurcharge} />
                                )}
                                <PriceRow label="GST" amount={order.pricing.gst} />
                                <PriceRow label="QST" amount={order.pricing.qst} />
                            </div>
                        )}

        <div className="history-card__bottom">
            <span className="list-card__price">{showTotal(order)}</span>
            <div className="history-card__actions">
            {canCancel(order) && (
                <Button variant="link" disabled={pendingId !== null} onClick={() => handleCancel(order)}>
                {pendingId === order.id ? "Saving…" : "Cancel order"}
                </Button>
            )}
            {canRefund(order) && confirmRefundId !== order.id && (
                <Button variant="secondary" disabled={pendingId !== null} onClick={() => setConfirmRefundId(order.id)}>
                {pendingId === order.id ? "Saving…" : "Refund"}
                </Button>
            )}
            {canRefund(order) && confirmRefundId === order.id && (
                <div className="history-card__confirm" role="group" aria-label="Confirm refund">
                <span className="meta">Refund {showTotal(order)} for this order?</span>
                <Button variant="link" onClick={() => setConfirmRefundId(null)}>Keep</Button>
                <Button onClick={() => handleRefund(order)}>Confirm refund</Button>
                </div>
            )}
            </div>
        </div>
    </li>
  );

  const ordersIn = (t: TabLabel) => orders?.filter((o) => TABS[t]?.includes(o.status) ?? true) ?? [];
  const shown = ordersIn(tab);

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
            <div className="history-tabs" role="tablist">
              {(Object.keys(TABS) as TabLabel[]).map((t) => (
                <button
                  key={t}
                  role="tab"
                  aria-selected={tab === t}
                  className={`history-tab${tab === t ? " history-tab--active" : ""}`}
                  onClick={() => setTab(t)}
                >
                  {t} ({ordersIn(t).length})
                </button>
              ))}
            </div>

            <section className="card history-section" aria-label={`${tab} orders`}>
              {shown.length === 0 ? (
                <p className="page__status meta">
                  {tab === "All" ? "No orders yet." : `No ${tab.toLowerCase()} orders.`}
                </p>
              ) : (
                <ul className="page__list">{shown.map(renderOrder)}</ul>
              )}
            </section>
          </>
        )}
      </main>
    </div>
  );
}