import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import AppHeader from "./components/AppHeader";
import Button from "./components/Button";
import Heading from "./components/Heading";
import ActiveOrderDetails from "./components/ActiveOrderDetails";
import Tag from "./components/Tag";
import { MapCard } from "./dashboard/LiveMap";
import type { ActiveDelivery } from "./orders/types/order";
import "./styles/Dashboard.css";

export default function CourierHomePage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const [active, setActive] = useState(false);

  // Load the courier's assigned order once the backend is connected
  const [activeDelivery] = useState<ActiveDelivery | null>(null);

  // A courier can't go offline in the middle of a delivery.
  const canEndDriving = active && !activeDelivery;

  const handleEndDriving = () => {
    if (!canEndDriving) return;
    setActive(false);
  };

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
          {/* Send the courier's availability to the backend */}
          <div className="intro-card__actions">
            <Button onClick={() => setActive(true)} disabled={active}>
              Start Driving
            </Button>
            <Button variant="secondary" onClick={handleEndDriving} disabled={!canEndDriving}>
              End Driving
            </Button>
            <Tag tone={active ? "sage" : "neutral"}>{active ? "Online: Let's get this party started" : "Offline: The dishes are too crazy"}</Tag>
          </div>
          {active && activeDelivery && (
            <p className="intro-card__text meta">
              Finish your active delivery before you end driving.
            </p>
          )}
        </section>

        <div className="dashboard-grid">
          <MapCard active={active} emptyMessage="Start driving to see the map" />

          <section className="dashboard-card dashboard-card--active card">
            <Heading level={3}>Active delivery</Heading>
            {activeDelivery ? (
              <ActiveOrderDetails order={activeDelivery} />
            ) : (
              <p className="dashboard-card__empty meta">No active delivery right now.</p>
            )}
          </section>

          <section className="dashboard-card dashboard-card--action card">
            {/* Call the pick-up / deliver endpoints once they exist */}
            <Button block disabled={!activeDelivery}>
              {!activeDelivery
                ? "No deliveries"
                : activeDelivery.status === "READY"
                  ? "Pick Up Order"
                  : "Deliver"}
            </Button>
          </section>
        </div>
      </main>
    </div>
  );
}
