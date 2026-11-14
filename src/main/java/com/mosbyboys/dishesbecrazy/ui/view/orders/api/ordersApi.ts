import { loggedFetch, readJson } from "../../api/loggedFetch";
import type { HistoryOrder, LedgerEntry, RestaurantOrder } from "../types/order";

const API = "http://localhost:8080";

export async function getRestaurantOrders(token: string): Promise<RestaurantOrder[]> {
  const path = "/api/restaurant/orders";

  const response = await loggedFetch(API + path, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return readJson(response);
}
// Queue reads now share command, HTTP, network, and JSON error logging.

// The action endpoints reply 204 with no body, so there is nothing to parse on success.
export async function post(path: string, token: string, body?: unknown): Promise<void> {
  await loggedFetch(API + path, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body !== undefined && { "Content-Type": "application/json" }),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}
// Logs every order action while preserving the existing empty-success-response contract.

export function acceptOrder(token: string, orderId: string): Promise<void> {
  return post(`/api/orders/${orderId}/accept`, token);
}

export function rejectOrder(token: string, orderId: string, reason: string): Promise<void> {
  return post(`/api/orders/${orderId}/reject`, token, { reason });
}

export function prepareOrder(token: string, orderId: string): Promise<void> {
  return post(`/api/orders/${orderId}/prepare`, token);
}

export function markOrderReady(token: string, orderId: string): Promise<void> {
  return post(`/api/orders/${orderId}/ready`, token);
}

// The logged-in user's order history. The backend picks which orders to return from the token's role.
export async function getOrderHistory(token: string): Promise<HistoryOrder[]> {
  const path = "/api/orders/history";

  const response = await loggedFetch(API + path, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return readJson(response);
}
// History requests use the same logging path, including errors that occur before an HTTP response.

export function cancelOrder(token: string, orderId: string): Promise<void> {
  return post(`/api/orders/${orderId}/cancel`, token);
}

export function refundOrder(token: string, orderId: string): Promise<void> {
  return post(`/api/orders/${orderId}/refund`, token);
}

// Every charge and refund, newest first (operator only).
export async function getLedger(token: string): Promise<LedgerEntry[]> {
  const response = await loggedFetch(API + "/api/ledger", {
    headers: { Authorization: `Bearer ${token}` },
  });
  return readJson(response);
}
