import type { Restaurant } from "./types/menu";

// Tag tone and label for each restaurant status, shared by the restaurant list and the map's details panel.
export const RESTAURANT_STATUS_TAG: Record<
  Restaurant["status"],
  { tone: "sage" | "terracotta" | "neutral"; label: string }
> = {
  OPEN: { tone: "sage", label: "Open" },
  PAUSED: { tone: "terracotta", label: "Paused" },
  CLOSED: { tone: "neutral", label: "Closed" },
};
