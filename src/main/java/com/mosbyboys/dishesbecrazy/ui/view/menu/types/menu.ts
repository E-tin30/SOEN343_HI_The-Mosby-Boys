// The JSON shapes returned by the menu endpoints.
// These must match the Java response records in RestaurantController and MenuController.

// GET /api/restaurants  →  RestaurantResponse(UUID id, String name, String status)
export type Restaurant = {
  id: string;
  name: string;
  status: "OPEN" | "PAUSED" | "CLOSED";
};

// GET /api/restaurants/{id}/menu  →  MenuItemResponse(UUID id, String name, BigDecimal price, String status, String description)
export type MenuItem = {
  id: string;
  name: string;
  price: number;
  status: "AVAILABLE" | "SOLD_OUT";
  description: string | null;
};
