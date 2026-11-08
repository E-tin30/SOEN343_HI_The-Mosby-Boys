import { loggedFetch, readJson } from "../../api/loggedFetch";
import type { Restaurant, MenuItem } from "../types/menu";

const API = "http://localhost:8080";

async function get<T>(path: string, token: string, signal?: AbortSignal): Promise<T> {
  const response = await loggedFetch(API + path, {
    headers: { Authorization: `Bearer ${token}` },
    signal,
  });
  // Logs menu/restaurant reads through the shared helper and allows obsolete checks to be canceled.
  return readJson(response);
  // Reports JSON parsing failures through the same output console as request failures.
}

export function getRestaurants(token: string, signal?: AbortSignal): Promise<Restaurant[]> {
  return get("/api/restaurants", token, signal);
}

export function getMenu(token: string, restaurantId: string, signal?: AbortSignal): Promise<MenuItem[]> {
  return get(`/api/restaurants/${restaurantId}/menu`, token, signal);
}
