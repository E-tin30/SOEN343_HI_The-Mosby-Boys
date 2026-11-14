import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import RestaurantStatusControl from "../components/RestaurantStatusControl";
import Tag from "../components/Tag";
import {
  acceptOrder,
  getRestaurantOrders,
  markOrderReady,
  prepareOrder,
  rejectOrder,
} from "./api/ordersApi";
import type { QueueStatus, RestaurantOrder } from "./types/order";
import "../styles/RestaurantOrdersPage.css";

type Queue = {
  title: string;
  statuses: QueueStatus[];
  emptyText: string;
};

const QUEUES: Queue[] = [
  { title: "Incoming Orders", statuses: ["PLACED"], emptyText: "No new orders." },
  { title: "Active Orders", statuses: ["ACCEPTED", "PREPARING"], emptyText: "Nothing in the kitchen." },
  { title: "Ready", statuses: ["READY"], emptyText: "Nothing waiting for pickup." },
];

type OrderAction = {
  label: string;
  variant: "secondary" | "link";
  next: QueueStatus | null;
};

// Buttons that move an order along; Ready is the last stop.
const ACTIONS: Record<QueueStatus, OrderAction[]> = {
  PLACED: [
    { label: "Reject", variant: "link", next: null },
    { label: "Accept", variant: "secondary", next: "ACCEPTED" },
  ],
  ACCEPTED: [{ label: "Start preparing", variant: "secondary", next: "PREPARING" }],
  PREPARING: [{ label: "Mark ready", variant: "secondary", next: "READY" }],
  READY: [],
};

const outOfTurnReason = (order: RestaurantOrder, action: OrderAction): string | null => {
  if (order.status === "PLACED" && action.next === "ACCEPTED" && !order.nextToAccept) {
    return "Orders are accepted in the order they were placed.";
  }
  if (order.status === "ACCEPTED" && !order.nextToPrepare) {
    return "Orders are prepared in the order they were accepted.";
  }
  return null;
};

const formatTime = (iso: string) =>
  new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });

export default function RestaurantOrdersPage() {
  const navigate = useNavigate();
  const { accessToken } = useAuth();
  const [orders, setOrders] = useState<RestaurantOrder[] | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [error, setError] = useState("");

  const loadOrders = useCallback(async () => {
    if (!accessToken) return;
    try {
      setOrders(await getRestaurantOrders(accessToken));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't load orders.");
    }
  }, [accessToken]);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const runAction = async (order: RestaurantOrder, action: OrderAction) => {
    if (!accessToken) return;

    let call: Promise<void>;
    switch (order.status) {
      case "PLACED":
        if (action.next === null) {
          const reason = window.prompt("Why are you rejecting this order?")?.trim();
          if (!reason) return;
          call = rejectOrder(accessToken, order.id, reason);
        } else {
          call = acceptOrder(accessToken, order.id);
        }
        break;
      case "ACCEPTED":
        call = prepareOrder(accessToken, order.id);
        break;
      case "PREPARING":
        call = markOrderReady(accessToken, order.id);
        break;
      default:
        return;
    }

    setPendingId(order.id);
    setError("");
    try {
      await call;
      // Reload so the queues (and who is next to prepare) match the backend exactly.
      await loadOrders();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setPendingId(null);
    }
  };

  return (
    <div className="page">
      <AppHeader>
        <Button variant="secondary" onClick={() => navigate("/restaurant/home")}>
          Back to home
        </Button>
        <RestaurantStatusControl variant="header" />
      </AppHeader>

      <main className="orders-board">
        {error && (
          <p className="page__status page__status--error" role="alert">
            Error: {error}
          </p>
        )}

        {!error && orders === null && <p className="page__status meta">Loading…</p>}

        {orders && (
          <div className="orders-board__queues">
            {QUEUES.map((queue) => {
              const queueOrders = orders.filter((o) => queue.statuses.includes(o.status));
              return (
                <section key={queue.title} className="order-queue card" aria-label={queue.title}>
                  <header className="order-queue__header">
                    <Heading level={2}>{queue.title}</Heading>
                    <Tag>{String(queueOrders.length)}</Tag>
                  </header>

                  {queueOrders.length === 0 ? (
                    <p className="page__status meta">{queue.emptyText}</p>
                  ) : (
                    <ul className="page__list">
                      {queueOrders.map((order) => (
                        <li key={order.id} className="list-card order-card">
                          <div className="order-card__top">
                            <Heading level={3}>{`Order #${order.id.slice(-6)}`}</Heading>
                            <span className="meta">{formatTime(order.placedAt)}</span>
                          </div>

                          {order.status === "PREPARING" && (
                            <Tag tone="mustard" className="order-card__tag">In preparation</Tag>
                          )}

                          <ul className="order-card__lines">
                            {order.lines.map((line) => (
                              <li key={line.menuItemId}>
                                <span className="order-card__qty">{line.quantity}×</span> {line.menuItemName}
                              </li>
                            ))}
                          </ul>

                          <div className="order-card__bottom">
                            <span className="list-card__price">${order.total.toFixed(2)}</span>
                            {ACTIONS[order.status].length > 0 && (
                              <div className="order-card__actions">
                                {ACTIONS[order.status].map((action) => (
                                  <Button
                                    key={action.label}
                                    variant={action.variant}
                                    disabled={pendingId !== null || outOfTurnReason(order, action) !== null}
                                    title={outOfTurnReason(order, action) ?? undefined}
                                    onClick={() => runAction(order, action)}
                                  >
                                    {pendingId === order.id ? "Saving…" : action.label}
                                  </Button>
                                ))}
                              </div>
                            )}
                          </div>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
}
