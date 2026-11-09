import type { HistoryOrder, RestaurantOrder } from "../types/order";
import { log } from "../../outputLog";

const API = "http://localhost:8080";

export async function getRestaurantOrders(token: string): Promise<RestaurantOrder[]> {
  const path = "/api/restaurant/orders";
  log("command", `GET ${path}`);
  const response = await fetch(API + path, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    const message = (await response.text()) || `Request failed (${response.status})`;
    log("error", `GET ${path} → ${response.status}: ${message}`);
    throw new Error(message);
  }
  return response.json();
}

// The action endpoints reply 204 with no body, so there is nothing to parse on success.
export async function post(path: string, token: string, body?: unknown): Promise<void> {
  log("command", `POST ${path}`);
  const response = await fetch(API + path, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body !== undefined && { "Content-Type": "application/json" }),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!response.ok) {
    const message = (await response.text()) || `Request failed (${response.status})`;
    log("error", `POST ${path} → ${response.status}: ${message}`);
    throw new Error(message);
  }
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
  log("command", `GET ${path}`);
  const response = await fetch(API + path, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    const message = (await response.text()) || `Request failed (${response.status})`;
    log("error", `GET ${path} → ${response.status}: ${message}`);
    throw new Error(message);
  }
  return response.json();
}

export function cancelOrder(token: string, orderId: string): Promise<void> {
  return post(`/api/orders/${orderId}/cancel`, token);
}

export function refundOrder(token: string, orderId: string): Promise<void> {
  return post(`/api/orders/${orderId}/refund`, token);
}