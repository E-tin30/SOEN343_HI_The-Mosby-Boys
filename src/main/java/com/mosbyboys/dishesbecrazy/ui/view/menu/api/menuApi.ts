import type { Restaurant, MenuItem } from "../types/menu";
import { log } from "../../outputLog";

const API = "http://localhost:8080";

async function get<T>(path: string, token: string): Promise<T> {
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

export function getRestaurants(token: string): Promise<Restaurant[]> {
  return get("/api/restaurants", token);
}

export function getMenu(token: string, restaurantId: string): Promise<MenuItem[]> {
  return get(`/api/restaurants/${restaurantId}/menu`, token);
}
