import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { useCart } from "./CartContext";
import AppHeader from "./components/AppHeader";
import ActiveOrderDetails from "./components/ActiveOrderDetails";
import Button from "./components/Button";
import Heading from "./components/Heading";
import { MapCard } from "./dashboard/LiveMap";
import { getOrderHistory } from "./orders/api/ordersApi";
import type { ActiveOrderSummary, HistoryStatus } from "./orders/types/order";
import "./styles/Dashboard.css";

// Statuses for an order that hasn't reached the customer yet.
const IN_PROGRESS: HistoryStatus[] = ["PLACED", "ACCEPTED", "PREPARING", "READY", "PICKEDUP"];
import DemoResetControl from "./components/DemoResetControl";
import "./styles/HomePage.css";

export default function HomePage() {
  const navigate = useNavigate();
  const { user, accessToken, logout } = useAuth();
  const { itemCount } = useCart();
  const isCustomer = user?.role === "CUSTOMER";
  const [activeOrder, setActiveOrder] = useState<ActiveOrderSummary | null>(null);
  const [activeCount, setActiveCount] = useState(0);

  // Show the customer's in-progress order that's furthest along.
  useEffect(() => {
    if (!accessToken) return;
    getOrderHistory(accessToken)
      .then((orders) => {
        const inProgress = orders.filter((o) => IN_PROGRESS.includes(o.status));
        setActiveCount(inProgress.length);
        const latest = inProgress.sort(
          (a, b) =>
            IN_PROGRESS.indexOf(b.status) - IN_PROGRESS.indexOf(a.status) ||
            a.placedAt.localeCompare(b.placedAt)
        )[0];
        setActiveOrder(
          latest
            ? {
                id: latest.id,
                restaurantName: latest.restaurantName,
                status: latest.status,
                lines: latest.lines,
                total: latest.pricing?.total ?? latest.total,
              }
            : null
        );
      })
      .catch(() => {
        setActiveOrder(null);
        setActiveCount(0);
      });
  }, [accessToken]);

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
          <Heading>{`Hey ${user?.firstName ?? "there"}, ~hungry?~`}</Heading>
          <p className="intro-card__text meta">
            Find home cooks and restaurants near you.
          </p>
          <Button onClick={() => navigate("/restaurants")}>
            Browse restaurants
          </Button>
        </section>

        <div className="dashboard-grid">
          <MapCard active={activeOrder !== null} emptyMessage="No active deliveries" />

          <section className="dashboard-card dashboard-card--active card">
            <Heading level={3}>{`Active orders (${activeCount})`}</Heading>
            {activeOrder ? (
              <ActiveOrderDetails order={activeOrder} />
            ) : (
              <p className="dashboard-card__empty meta">No active order right now.</p>
            )}
            <Button
              variant="link"
              className="dashboard-card__footer"
              onClick={() => navigate("/orders/history")}
            >
              View more
            </Button>
          </section>

          {/* Same as the menu page's cart button; /cart is customer-only. */}
          {isCustomer && (
            <section className="dashboard-card dashboard-card--action card">
              <Button block onClick={() => navigate("/cart")}>
                View cart ({itemCount})
              </Button>
            </section>
          )}
        </div>
      </main>
    </div>
  );
}
