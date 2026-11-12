// Statuses a restaurant sees in its queues, named after the backend order states.
export type QueueStatus = "PLACED" | "ACCEPTED" | "PREPARING" | "READY";
import type { PricingBreakdown } from "./PricingBreakdown";

export type OrderLine = {
  menuItemId: string;
  menuItemName: string;
  quantity: number;
  unitPrice: number;
};

export type RestaurantOrder = {
  id: string;
  status: QueueStatus;
  placedAt: string;
  lines: OrderLine[];
  total: number;
  // True only for the placed order at the front of the incoming line.
  nextToAccept: boolean;
  // True only for the accepted order at the front of the preparation line.
  nextToPrepare: boolean;
};

// Every status an order can show in history: the backend state name without "State", upper-cased.
export type HistoryStatus =
  | "PLACED" | "ACCEPTED" | "PREPARING" | "READY"
  | "PICKEDUP" | "DELIVERED"
  | "CANCELLED" | "REJECTED" | "REFUNDED";

export type HistoryOrder = {
  id: string;
  restaurantId: string;
  restaurantName: string;
  customerName: string | null;
  status: HistoryStatus;
  placedAt: string;
  lines: OrderLine[];
  total: number | null;
  pricing: PricingBreakdown | null;
  rejectionReason: string | null;
};

// What the home dashboards show for an order in progress.
export type ActiveOrderSummary = {
  id: string;
  restaurantName: string;
  status: HistoryStatus;
  lines: OrderLine[];
  total: number | null;
};

// A courier's current delivery: waiting at the restaurant (READY) or on the way to the customer (PICKEDUP).
export type ActiveDelivery = ActiveOrderSummary & {
  status: Extract<HistoryStatus, "READY" | "PICKEDUP">;
};

export type OrderResponse = {
  id: string;
  restaurantId: string;
  state: string;
  subtotal: number;
  orderLines: OrderLine[];
}
