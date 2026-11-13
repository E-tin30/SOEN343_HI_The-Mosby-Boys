import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import AppHeader from "./components/AppHeader";
import Button from "./components/Button";
import Heading from "./components/Heading";
import RestaurantStatusControl from "./components/RestaurantStatusControl";
import Tag from "./components/Tag";
import { restaurantLoad } from "./dashboard/LiveMap";
import { getMenu } from "./menu/api/menuApi";
import type { MenuItem, Restaurant } from "./menu/types/menu";
import { getRestaurantOrders } from "./orders/api/ordersApi";
import type { QueueStatus, RestaurantOrder } from "./orders/types/order";
import "./styles/Dashboard.css";
import "./styles/MenuPage.css";

// One line per step an order goes through. Together they add up to the total.
const QUEUE_SUMMARY: { label: string; status: QueueStatus }[] = [
  { label: "Incoming", status: "PLACED" },
  { label: "Accepted", status: "ACCEPTED" },
  { label: "In-preparation", status: "PREPARING" },
  { label: "Ready", status: "READY" },
];

// Tag under the order total: same colour as its circle, same words as the map legend.
const LOAD_TAG = {
  quiet: { tone: "sage", label: "Quiet" },
  steady: { tone: "mustard", label: "Steady" },
  busy: { tone: "tomato", label: "Busy" },
  paused: { tone: "terracotta", label: "Paused" },
  closed: { tone: "neutral", label: "Closed" },
} as const;

export default function RestaurantHomePage() {
  const navigate = useNavigate();
  const { user, accessToken, logout } = useAuth();
  // The restaurant itself (name and status), handed up by RestaurantStatusControl once it loads.
  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  const [menu, setMenu] = useState<MenuItem[] | null>(null);
  const [menuError, setMenuError] = useState("");
  const [orders, setOrders] = useState<RestaurantOrder[]>([]);

  // Same lookup as the edit page: a restaurant account's id is its restaurant's id.
  useEffect(() => {
    if (!accessToken || !user) return;
    getMenu(accessToken, user.id)
      .then(setMenu)
      .catch((e: Error) => setMenuError(e.message));
  }, [accessToken, user]);

  useEffect(() => {
    if (!accessToken) return;
    getRestaurantOrders(accessToken)
      .then(setOrders)
      .catch(() => setOrders([]));
  }, [accessToken]);

  // Coloured the way the live map colours this restaurant's marker: closed and paused have their own colours,
  // otherwise 6+ accepted orders waiting to be prepared is busy, 3-5 is steady, fewer is quiet.
  const load = restaurantLoad({
    status: restaurant?.status ?? "OPEN",
    queueLength: orders.filter((o) => o.status === "ACCEPTED").length,
  });
  const loadTag = LOAD_TAG[load];

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
          <Heading>{`Hey ${restaurant?.name ?? user?.firstName ?? "there"}, ~ready to get cooking?~`}</Heading>
          <p className="intro-card__text meta">
            Manage your menu and keep up with incoming orders.
          </p>
          <hr className="intro-card__divider" />
          <RestaurantStatusControl onRestaurant={setRestaurant} />
        </section>

        <div className="dashboard-grid">
          {/* The menu sits where the map does on the customer and courier homes. */}
          <section className="dashboard-card dashboard-card--menu card">
            <Heading level={2}>{"Your ~menu~"}</Heading>
            {menuError ? (
              <p className="page__status page__status--error" role="alert">{menuError}</p>
            ) : menu === null ? (
              <p className="dashboard-card__empty meta">Loading…</p>
            ) : menu.length === 0 ? (
              <p className="dashboard-card__empty meta">No menu items yet.</p>
            ) : (
              <ul className="page__list dashboard-menu dashboard-card__scroll">
                {menu.map((item) => {
                  const soldOut = item.status === "SOLD_OUT";
                  return (
                    <li
                      key={item.id}
                      className={`list-card menu-item dashboard-menu__item${soldOut ? " menu-item--sold-out" : ""}`}
                    >
                      <div className="list-card__info">
                        <span className="dashboard-menu__name">{item.name}</span>
                        <span className="caption meta">{`${item.stockQuantity} in stock`}</span>
                      </div>
                      <div className="list-card__side">
                        <span className="list-card__price">${item.price.toFixed(2)}</span>
                        {soldOut && <Tag>Sold out</Tag>}
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
            <div className="dashboard-card__footer dashboard-card__actions">
              <Button variant="secondary" onClick={() => navigate("/restaurant/add")}>
                Add Menu Item
              </Button>
              <Button variant="secondary" onClick={() => navigate("/restaurant/edit")}>
                Edit Menu Item
              </Button>
            </div>
          </section>

          <section className="dashboard-card dashboard-card--active card">
            <Heading level={2}>{"Active ~orders~"}</Heading>
            {/* Every order across the restaurant's queues. */}
            <span className={`dashboard-orders__total dashboard-orders__total--${load}`}>
              {orders.length}
            </span>
            <Tag tone={loadTag.tone} className="dashboard-orders__load">{loadTag.label}</Tag>
            <hr className="dashboard-card__divider" />
            <ul className="dashboard-orders__queues">
              {QUEUE_SUMMARY.map((queue) => (
                <li key={queue.label}>
                  {`${queue.label}: `}
                  <strong>{orders.filter((o) => o.status === queue.status).length}</strong>
                </li>
              ))}
            </ul>
          </section>

          {/* Bottom-right action slot, like the customer's View cart and the courier's delivery buttons. */}
          <section className="dashboard-card dashboard-card--action card">
            <Button block onClick={() => navigate("/restaurant/orders")}>
              View Orders
            </Button>
          </section>
        </div>
      </main>
    </div>
  );
}
