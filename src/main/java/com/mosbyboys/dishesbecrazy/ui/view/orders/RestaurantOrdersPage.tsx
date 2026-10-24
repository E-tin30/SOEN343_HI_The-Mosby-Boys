import { useNavigate } from "react-router-dom";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import Tag from "../components/Tag";
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

type OrderAction = { label: string; variant: "secondary" | "link" };

// Buttons that move an order along; Ready is the last stop.
const ACTIONS: Record<QueueStatus, OrderAction[]> = {
  PLACED: [
    { label: "Reject", variant: "link" },
    { label: "Accept", variant: "secondary" },
  ],
  ACCEPTED: [{ label: "Start preparing", variant: "secondary" }],
  PREPARING: [{ label: "Mark ready", variant: "secondary" }],
  READY: [],
};

// Sample Orders: replace with the restaurant's orders once the backend is set up.
const SAMPLE_ORDERS: RestaurantOrder[] = [
  {
    id: "a3f9c2d1-0000-0000-0000-000000000001",
    status: "PLACED",
    placedAt: "2026-10-23T18:42:00Z",
    lines: [
      { menuItemId: "1", name: "Margherita pizza", quantity: 2, unitPrice: 14 },
      { menuItemId: "2", name: "Caesar salad", quantity: 1, unitPrice: 9.5 },
    ],
    total: 37.5,
  },
  {
    id: "b71e04aa-0000-0000-0000-000000000002",
    status: "PLACED",
    placedAt: "2026-10-23T18:45:00Z",
    lines: [{ menuItemId: "3", name: "Spaghetti carbonara", quantity: 1, unitPrice: 16 }],
    total: 16,
  },
  {
    id: "c58d7b3e-0000-0000-0000-000000000003",
    status: "ACCEPTED",
    placedAt: "2026-10-23T18:30:00Z",
    lines: [
      { menuItemId: "4", name: "Lasagna", quantity: 1, unitPrice: 18 },
      { menuItemId: "5", name: "Tiramisu", quantity: 2, unitPrice: 7 },
    ],
    total: 32,
  },
  {
    id: "e9a4c870-0000-0000-0000-000000000005",
    status: "PREPARING",
    placedAt: "2026-10-23T18:25:00Z",
    lines: [{ menuItemId: "3", name: "Spaghetti carbonara", quantity: 2, unitPrice: 16 }],
    total: 32,
  },
  {
    id: "d02f6e19-0000-0000-0000-000000000004",
    status: "READY",
    placedAt: "2026-10-23T18:15:00Z",
    lines: [{ menuItemId: "1", name: "Margherita pizza", quantity: 1, unitPrice: 14 }],
    total: 14,
  },
];

const formatTime = (iso: string) =>
  new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });

export default function RestaurantOrdersPage() {
  const navigate = useNavigate();
  const orders = SAMPLE_ORDERS; // Replace with the restaurant's orders once the backend is set up.

  return (
    <div className="page">
      <AppHeader>
        <Button variant="secondary" onClick={() => navigate("/restaurant/home")}>
          Back to home
        </Button>
      </AppHeader>

      <main className="orders-board">
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
                          <Heading level={3}>{`Order #${order.id.slice(0, 6)}`}</Heading>
                          <span className="meta">{formatTime(order.placedAt)}</span>
                        </div>

                        {order.status === "PREPARING" && (
                          <Tag tone="mustard" className="order-card__tag">In preparation</Tag>
                        )}

                        <ul className="order-card__lines">
                          {order.lines.map((line) => (
                            <li key={line.menuItemId}>
                              <span className="order-card__qty">{line.quantity}×</span> {line.name}
                            </li>
                          ))}
                        </ul>

                        <div className="order-card__bottom">
                          <span className="list-card__price">${order.total.toFixed(2)}</span>
                          {ACTIONS[order.status].length > 0 && (
                            <div className="order-card__actions">
                              {/* TODO: move the order to the next queue. */}
                              {ACTIONS[order.status].map((action) => (
                                <Button key={action.label} variant={action.variant}>
                                  {action.label}
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
      </main>
    </div>
  );
}
