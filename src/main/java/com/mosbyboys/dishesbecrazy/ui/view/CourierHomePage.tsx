import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import AppHeader from "./components/AppHeader";
import Button from "./components/Button";
import Heading from "./components/Heading";
import ActiveOrderDetails from "./components/ActiveOrderDetails";
import Tag from "./components/Tag";
import { MapCard } from "./dashboard/LiveMap";
import {
  acceptOffer,
  declineOffer,
  deliverOrder,
  getCourierState,
  getCurrentOrders,
  getPendingOffer,
  goOffline,
  goOnline,
  pickUpOrder,
  type CourierState,
} from "./orders/api/courierApi";
import type { ActiveDelivery, HistoryOrder } from "./orders/types/order";
import "./styles/Dashboard.css";

// How often an on-duty courier rechecks the backend for offers and changes to their delivery.
const POLL_MS = 5000;

const STATE_TAG: Record<CourierState, { tone: "sage" | "mustard" | "navy" | "neutral"; label: string }> = {
  OFFLINE: { tone: "neutral", label: "Offline: The dishes are too crazy" },
  IDLE: { tone: "sage", label: "Idle: Let's get this party started" },
  ASSIGNED: { tone: "mustard", label: "Delivering: Heading to the restaurant" },
  CARRYING: { tone: "navy", label: "Delivering: On the way to the customer" },
};

const toDelivery = (order: HistoryOrder): ActiveDelivery => ({
  id: order.id,
  restaurantName: order.restaurantName,
  status: order.status as ActiveDelivery["status"],
  lines: order.lines,
  total: order.total,
});

export default function CourierHomePage() {
  const navigate = useNavigate();
  const { user, accessToken, logout } = useAuth();
  const [courierState, setCourierState] = useState<CourierState | null>(null);
  // An order offered to the courier that they haven't accepted or declined yet.
  const [offer, setOffer] = useState<HistoryOrder | null>(null);
  // The accepted order the courier is picking up or delivering.
  const [activeDelivery, setActiveDelivery] = useState<ActiveDelivery | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  // Only the newest refresh may update the page, so a poll sent before an action can't overwrite its result
  const latestRefresh = useRef(0);

  // Reread backend after eavry action to keep page in sync
  const refresh = useCallback(async () => {
    if (!accessToken || !user) return;
    const id = ++latestRefresh.current;
    const [state, pending, current] = await Promise.all([
      getCourierState(accessToken),
      getPendingOffer(accessToken),
      getCurrentOrders(accessToken),
    ]);
    if (id !== latestRefresh.current) return;
    setCourierState(state);
    setOffer(pending ? current.find((o) => o.id === pending.orderId) ?? null : null);
    const delivery = current.find((o) => o.id !== pending?.orderId);
    setActiveDelivery(delivery ? toDelivery(delivery) : null);
  }, [accessToken, user]);

  useEffect(() => {
    refresh().catch((e: Error) => setError(e.message));
  }, [refresh]);

  // While on duty, keep refreshing the backend
  useEffect(() => {
    if (courierState === null || courierState === "OFFLINE") return;
    const timer = window.setInterval(() => {
      refresh().catch(() => {});
    }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [courierState, refresh]);

  // Runs one courier action, then reloads everything so the page matches the backend
  const run = (action: (token: string) => Promise<void>) => async () => {
    if (!accessToken || busy) return;
    setBusy(true);
    setError("");
    try {
      await action(accessToken);
    } catch (e) {
      setError((e as Error).message);
    }
    try {
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  // A courier can only go offline while idle, never in the middle of a delivery
  const canEndDriving = courierState === "IDLE" && !busy;

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="page">
      <AppHeader>
        <Button variant="secondary" onClick={() => navigate("/orders/history")}>
          Order history
        </Button>
        <Button variant="secondary" onClick={handleLogout}>
          Log out
        </Button>
      </AppHeader>

      <main className="page__content">
        <section className="intro-card card">
          <p className="caption meta">Welcome back</p>
          <Heading>{`Hey ${user?.firstName ?? "there"}, ~ready to roll?~`}</Heading>
          <p className="intro-card__text meta">
            Start driving to receive delivery offers.
          </p>
          <div className="intro-card__actions">
            <Button onClick={run(goOnline)} disabled={courierState !== "OFFLINE" || busy}>
              Start Driving
            </Button>
            <Button variant="secondary" onClick={run(goOffline)} disabled={!canEndDriving}>
              End Driving
            </Button>
            {courierState && (
              <Tag tone={STATE_TAG[courierState].tone}>{STATE_TAG[courierState].label}</Tag>
            )}
          </div>
          {error && (
            <p className="page__status page__status--error" role="alert">
              {error}
            </p>
          )}
        </section>

        <div className="dashboard-grid">
          <MapCard active={courierState !== null && courierState !== "OFFLINE"} emptyMessage="Start driving to see the map" />

          <section className="dashboard-card dashboard-card--active card">
            {activeDelivery ? (
              <>
                <Heading level={3}>Active delivery</Heading>
                <ActiveOrderDetails order={activeDelivery} />
              </>
            ) : offer ? (
              <>
                <Heading level={3}>New offer</Heading>
                <ActiveOrderDetails order={toDelivery(offer)} />
              </>
            ) : (
              <>
                <Heading level={3}>Active delivery</Heading>
                <p className="dashboard-card__empty meta">
                  {courierState === "IDLE" ? "Waiting for an offer…" : "No active delivery right now."}
                </p>
              </>
            )}
          </section>

          <section className="dashboard-card dashboard-card--action card">
            {courierState === "ASSIGNED" && activeDelivery ? (
              <Button block onClick={run((t) => pickUpOrder(t, activeDelivery.id))} disabled={busy}>
                Pick Up Order
              </Button>
            ) : courierState === "CARRYING" && activeDelivery ? (
              <Button block onClick={run((t) => deliverOrder(t, activeDelivery.id))} disabled={busy}>
                Deliver
              </Button>
            ) : offer ? (
              <>
                <Button block onClick={run((t) => acceptOffer(t, offer.id))} disabled={busy}>
                  Accept
                </Button>
                <Button block variant="secondary" onClick={run((t) => declineOffer(t, offer.id))} disabled={busy}>
                  Decline
                </Button>
              </>
            ) : (
              <Button block disabled>
                No deliveries
              </Button>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}
