import type { HistoryStatus } from "./types/order";

// Label and Tag tone for each order status, shared by order history and the home dashboards.
export type Tone = "sage" | "mustard" | "tomato" | "navy" | "neutral";

export const STATUS_LABEL: Record<HistoryStatus, string> = {
  PLACED: "Placed",
  ACCEPTED: "Accepted",
  PREPARING: "Preparing",
  READY: "Ready",
  PICKEDUP: "Picked up",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
  REJECTED: "Rejected",
  REFUNDED: "Refunded",
};

export const STATUS_TONE: Record<HistoryStatus, Tone> = {
  PLACED: "mustard",
  ACCEPTED: "mustard",
  PREPARING: "mustard",
  READY: "mustard",
  PICKEDUP: "navy",
  DELIVERED: "sage",
  CANCELLED: "tomato",
  REJECTED: "tomato",
  REFUNDED: "navy",
};
