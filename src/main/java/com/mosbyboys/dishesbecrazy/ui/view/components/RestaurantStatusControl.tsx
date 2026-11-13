import { useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import { getRestaurants, openRestaurant, pauseRestaurant } from "../menu/api/menuApi";
import type { Restaurant } from "../menu/types/menu";
import Button from "./Button";
import Tag from "./Tag";
import "./RestaurantStatusControl.css";

// Same colours as the restaurant list (RestaurantsPage).
export const STATUS_TAG: Record<Restaurant["status"], { tone: "sage" | "mustard" | "neutral"; label: string }> = {
  OPEN: { tone: "sage", label: "Open" },
  PAUSED: { tone: "mustard", label: "Paused" },
  CLOSED: { tone: "neutral", label: "Closed" },
};


const PAUSED_TEXT = "New orders are blocked until the restaurant is reopened.";

const STATUS_COPY: Record<Restaurant["status"], { title: string; text: string }> = {
  OPEN: { title: "Open for orders", text: "Customers can see your menu and order now." },
  PAUSED: { title: "Orders paused", text: PAUSED_TEXT },
  CLOSED: { title: "Closed", text: "Customers can't order until you reopen." },
};

type RestaurantStatusControlProps = {
  // "card": its own card with the status spelled out (home page).
  // "header": compact, for an AppHeader; the Pause button while open, a status pill and Reopen otherwise (orders page).
  variant?: "card" | "header";
  // Called with the restaurant whenever it is loaded or changed, e.g. so a page can show its name.
  onRestaurant?: (restaurant: Restaurant) => void;
};

// The logged-in restaurant's status, with Pause shown while it is open (R-DASH-01) and Reopen
// while it is paused or closed (team decision). Used on the restaurant home page and the orders page
// (Dinner Rush: pause while watching the queue, reopen once caught up).
export default function RestaurantStatusControl({ variant = "card", onRestaurant }: RestaurantStatusControlProps) {
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
  const pauseButton = (
    <Button variant="secondary" onClick={() => changeStatus(pauseRestaurant)}>
      <span className="status-control__icon" aria-hidden="true">⏸</span> Pause orders
    </Button>
  );
  const reopenButton = (
    <Button variant="secondary" onClick={() => changeStatus(openRestaurant)}>
      <span className="status-control__icon" aria-hidden="true">▶</span> Reopen orders
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
    <section className="status-control card">
      {error && (
        <p className="page__status page__status--error" role="alert">
          Error: {error}
        </p>
      )}

      {restaurant === null && !error && <p className="meta">Loading…</p>}

      {restaurant && (
        <div className={`status-control__row status-control--${restaurant.status.toLowerCase()}`}>
          <div className="status-control__info">
            <span className="status-control__dot" />
            <div>
              <p className="status-control__title">{STATUS_COPY[restaurant.status].title}</p>
              <p className="status-control__text">{STATUS_COPY[restaurant.status].text}</p>
            </div>
          </div>
          {isOpen ? pauseButton : reopenButton}
        </div>
      )}
    </section>
  );
}
