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

async function post<T>(path: string, token: string): Promise<T> {  
  const response = await loggedFetch(API + path, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
  return readJson(response);
}

export function getRestaurants(token: string, signal?: AbortSignal): Promise<Restaurant[]> {
  return get("/api/restaurants", token, signal);
}

export function getMenu(token: string, restaurantId: string, signal?: AbortSignal): Promise<MenuItem[]> {
  return get(`/api/restaurants/${restaurantId}/menu`, token, signal);
}

export function pauseRestaurant(token: string, restaurantId: string): Promise<Restaurant> {
  return post(`/api/restaurants/${restaurantId}/pause`, token);
}

export function openRestaurant(token: string, restaurantId: string): Promise<Restaurant> {
  return post(`/api/restaurants/${restaurantId}/open`, token);
}

export function closeRestaurant(token: string, restaurantId: string): Promise<Restaurant> {
  return post(`/api/restaurants/${restaurantId}/close`, token);
}
