// Statuses a restaurant sees in its queues, named after the backend order states.
export type QueueStatus = "PLACED" | "ACCEPTED" | "PREPARING" | "READY";

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

// One row of GET /api/orders/history.
export type HistoryOrder = {
  id: string;
  restaurantId: string;
  restaurantName: string;
  // Only set for operators, null for every other role.
  customerName: string | null;
  status: HistoryStatus;
  placedAt: string;
  lines: OrderLine[];
  total: number | null;
  // Only set when the order was rejected.
  rejectionReason: string | null;
};

export type OrderResponse = {
  id: string;
  restaurantId: string;
  state: string;
  subtotal: number;
  orderLines: OrderLine[];
}
