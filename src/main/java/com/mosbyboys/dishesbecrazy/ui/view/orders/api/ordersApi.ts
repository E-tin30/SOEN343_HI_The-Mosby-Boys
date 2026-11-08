import { loggedFetch, readJson } from "../../api/loggedFetch";
import type { HistoryOrder, RestaurantOrder } from "../types/order";

const API = "http://localhost:8080";

export async function getRestaurantOrders(token: string): Promise<RestaurantOrder[]> {
  const path = "/api/restaurant/orders";

  const response = await loggedFetch(API + path, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return readJson(response);
}

// The action endpoints reply 204 with no body, so there is nothing to parse on success.
async function post(path: string, token: string, body?: unknown): Promise<void> {
  await await loggedFetch(API + path, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body !== undefined && { "Content-Type": "application/json" }),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

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

export function cancelOrder(token: string, orderId: string): Promise<void> {
  return post(`/api/orders/${orderId}/cancel`, token);
}

export function refundOrder(token: string, orderId: string): Promise<void> {
  return post(`/api/orders/${orderId}/refund`, token);
}