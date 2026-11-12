import type { HistoryOrder } from "../types/order";
import { log } from "../../outputLog";
import { post } from "./ordersApi";

const API = "http://localhost:8080";

export type CourierState = "OFFLINE" | "IDLE" | "ASSIGNED" | "CARRYING";

export type PendingOffer = {
  orderId: string;
  restaurantId: string;
};

// GET that returns null on 204 No Content (e.g. no pending offer).
async function get<T>(path: string, token: string): Promise<T | null> {
  log("command", `GET ${path}`);
  const response = await fetch(API + path, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    const message = (await response.text()) || `Request failed (${response.status})`;
    log("error", `GET ${path} → ${response.status}: ${message}`);
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}

// The logged-in courier's own state
export async function getCourierState(token: string): Promise<CourierState> {
  const response = await get<{ state: CourierState }>("/api/courier/state", token);
  if (!response) throw new Error("Courier state was missing from the response");
  return response.state;
}

export function getPendingOffer(token: string): Promise<PendingOffer | null> {
  return get<PendingOffer>("/api/courier/offers/pending", token);
}

// Orders offered to, accepted by or being carried by the courier, oldest first.
export async function getCurrentOrders(token: string): Promise<HistoryOrder[]> {
  return (await get<HistoryOrder[]>("/api/orders/courier/current", token)) ?? [];
}

export function goOnline(token: string): Promise<void> {
  return post("/api/courier/online", token);
}

export function goOffline(token: string): Promise<void> {
  return post("/api/courier/offline", token);
}

export function acceptOffer(token: string, orderId: string): Promise<void> {
  return post(`/api/courier/offers/${orderId}/accept`, token);
}

// The backend also takes the courier offline when they decline.
export function declineOffer(token: string, orderId: string): Promise<void> {
  return post(`/api/courier/offers/${orderId}/reject`, token);
}

export function pickUpOrder(token: string, orderId: string): Promise<void> {
  return post(`/api/courier/orders/${orderId}/pickup`, token);
}

export function deliverOrder(token: string, orderId: string): Promise<void> {
  return post(`/api/courier/orders/${orderId}/deliver`, token);
}
