import { useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import { closeRestaurant, getRestaurants, openRestaurant, pauseRestaurant } from "../menu/api/menuApi";
import type { Restaurant } from "../menu/types/menu";
import Button from "./Button";
import StatusBar, { type StatusTone } from "./StatusBar";
import Tag from "./Tag";
import "./RestaurantStatusControl.css";

// Same colours as the restaurant list (RestaurantsPage).
export const STATUS_TAG: Record<Restaurant["status"], { tone: "sage" | "terracotta" | "neutral"; label: string }> = {
  OPEN: { tone: "sage", label: "Open" },
  PAUSED: { tone: "terracotta", label: "Paused" },
  CLOSED: { tone: "neutral", label: "Closed" },
};


const PAUSED_TEXT = "New orders are blocked until the restaurant is reopened.";

const STATUS_COPY: Record<Restaurant["status"], { tone: StatusTone; title: string; text: string }> = {
  OPEN: { tone: "sage", title: "Open for orders", text: "Customers can see your menu and place orders." },
  PAUSED: { tone: "terracotta", title: "Orders paused", text: PAUSED_TEXT },
  CLOSED: { tone: "cocoa", title: "Closed", text: "Customers can't order until you reopen." },
};

type RestaurantStatusControlProps = {
  // "inline": the status spelled out in a full-width row, placed inside another card (home page's intro card).
  // "header": compact, for an AppHeader; the Pause button while open, a status pill and Reopen otherwise (orders page).
  variant?: "inline" | "header";
  // Called with the restaurant whenever it is loaded or changed, e.g. so a page can show its name.
  onRestaurant?: (restaurant: Restaurant) => void;
};

// The logged-in restaurant's status, with Pause shown while it is open (R-DASH-01) and Reopen
// while it is paused or closed (team decision). Used on the restaurant home page and the orders page
// (Dinner Rush: pause while watching the queue, reopen once caught up).
export default function RestaurantStatusControl({ variant = "inline", onRestaurant }: RestaurantStatusControlProps) {
  const { accessToken } = useAuth();
  const [restaurant, setRestaurantState] = useState<Restaurant | null>(null);
  const [error, setError] = useState("");

  const setRestaurant = (r: Restaurant) => {
    setRestaurantState(r);
    onRestaurant?.(r);
  };

  useEffect(() => {
    if (!accessToken) return;
    getRestaurants(accessToken)
      .then((restaurants) => {
        // A restaurant account only ever gets its own restaurant back (ORDFacade.getRestaurants).
        if (restaurants.length > 0) {
          setRestaurant(restaurants[0]);
        }
      })
      .catch((e: Error) => setError(e.message));
  }, [accessToken]);

  // Pause and Reopen share the same handling: show the updated restaurant, or the error.
  const changeStatus = (change: typeof pauseRestaurant) => {
    if (!accessToken || !restaurant) return;
    change(accessToken, restaurant.id)
      .then((updatedRestaurant) => {
        setRestaurant(updatedRestaurant);
        setError(""); // Clear any previous error
      })
      .catch((e: Error) => {
        setError(e.message);
      });
  };

  const isOpen = restaurant?.status === "OPEN";
  const isClosed = restaurant?.status === "CLOSED";
  const pauseButton = (
    <Button variant="secondary" onClick={() => changeStatus(pauseRestaurant)}>
      <span className="status-bar__icon" aria-hidden="true">⏸</span> Pause orders
    </Button>
  );
  const reopenButton = (
    <Button variant="secondary" onClick={() => changeStatus(openRestaurant)}>
      <span className="status-bar__icon" aria-hidden="true">▶</span> {isClosed ? "Open restaurant" : "Reopen orders"}
    </Button>
  );
  // Close sits next to Pause/Reopen while the restaurant is open or paused; once closed only Open is shown.
  const closeButton = (
    <Button variant="secondary" onClick={() => changeStatus(closeRestaurant)}>
      <span className="status-bar__icon" aria-hidden="true">■</span> Close restaurant
    </Button>
  );

  if (variant === "header") {
    return (
      <>
        {isOpen && pauseButton}
        {restaurant && !isOpen && (
          // Hovering the pill explains a pause.
          <span title={restaurant.status === "PAUSED" ? PAUSED_TEXT : undefined}>
            <Tag tone={STATUS_TAG[restaurant.status].tone}>{STATUS_TAG[restaurant.status].label}</Tag>
          </span>
        )}
        {restaurant && !isOpen && reopenButton}
        {error && (
          <span role="alert">
            <Tag tone="tomato">{error}</Tag>
          </span>
        )}
      </>
    );
  }

  return (
    <section className="status-control">
      {error && (
        <p className="page__status page__status--error" role="alert">
          Error: {error}
        </p>
      )}

      {restaurant === null && !error && <p className="meta">Loading…</p>}

      {restaurant && (
        <StatusBar {...STATUS_COPY[restaurant.status]}>
          <div className="status-control__actions">
            {isOpen ? pauseButton : reopenButton}
            {!isClosed && closeButton}
          </div>
        </StatusBar>
      )}
    </section>
  );
}
