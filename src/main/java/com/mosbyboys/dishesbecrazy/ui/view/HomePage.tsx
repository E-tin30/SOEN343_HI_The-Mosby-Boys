import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import { useCart } from "./CartContext";
import AppHeader from "./components/AppHeader";
import ActiveOrderDetails from "./components/ActiveOrderDetails";
import Button from "./components/Button";
import Heading from "./components/Heading";
import { getOrderHistory } from "./orders/api/ordersApi";
import type { ActiveOrderSummary, HistoryStatus } from "./orders/types/order";
import "./styles/HomePage.css";
import "./styles/Dashboard.css";

// Statuses for an order that hasn't reached the customer yet.
const IN_PROGRESS: HistoryStatus[] = ["PLACED", "ACCEPTED", "PREPARING", "READY", "PICKEDUP"];

export default function HomePage() {
  const navigate = useNavigate();
  const { user, accessToken, logout } = useAuth();
  const { itemCount } = useCart();
  const isCustomer = user?.role === "CUSTOMER";
  const [activeOrder, setActiveOrder] = useState<ActiveOrderSummary | null>(null);

  // Show the customer's most recent order that's still in progress.
  useEffect(() => {
    if (!accessToken) return;
    getOrderHistory(accessToken)
      .then((orders) => {
        const latest = orders
          .filter((o) => IN_PROGRESS.includes(o.status))
          .sort((a, b) => b.placedAt.localeCompare(a.placedAt))[0];
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
      .catch(() => setActiveOrder(null));
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
        <section className="home-card card">
          <p className="caption meta">Welcome back</p>
          <Heading>{`Hey ${user?.firstName ?? "there"}, ~hungry?~`}</Heading>
          <p className="home-card__text meta">
            Find home cooks and restaurants near you.
          </p>
          <Button onClick={() => navigate("/restaurants")}>
            Browse restaurants
          </Button>
          <Button onClick={() => navigate("/dashboard")}>
            View Dashboard Map
          </Button>
        </section>

        <div className="dashboard-grid">
          <section className="dashboard-card dashboard-card--map card">
            {/* TODO: render the delivery map here. */}
            <div className="dashboard-card__map meta">Map coming soon</div>
          </section>

          <section className="dashboard-card dashboard-card--active card">
            <Heading level={3}>Active order</Heading>
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
