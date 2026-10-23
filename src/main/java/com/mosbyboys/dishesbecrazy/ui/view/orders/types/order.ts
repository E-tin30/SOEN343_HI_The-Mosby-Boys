// Statuses a restaurant sees in its queues, named after the backend order states.
export type QueueStatus = "PLACED" | "ACCEPTED" | "PREPARING" | "READY";

export type OrderLine = {
  menuItemId: string;
  name: string;
  quantity: number;
  unitPrice: number;
};

export type RestaurantOrder = {
  id: string;
  status: QueueStatus;
  placedAt: string;
  lines: OrderLine[];
  total: number;
};
