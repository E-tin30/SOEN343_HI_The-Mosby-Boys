import { useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import { getRestaurants, pauseRestaurant } from "../menu/api/menuApi";
import type { Restaurant } from "../menu/types/menu";
import Button from "./Button";
import Heading from "./Heading";

type RestaurantStatusControlProps = {
  className?: string;
  // Called with the restaurant whenever it is loaded or changed, e.g. so a page can show its name.
  onRestaurant?: (restaurant: Restaurant) => void;
};

// The logged-in restaurant's status, with Pause shown only while it is open (R-DASH-01).
// Used on the restaurant home page and the orders page (Dinner Rush: pause while watching the queue).
export default function RestaurantStatusControl({ className = "card", onRestaurant }: RestaurantStatusControlProps) {
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

  const handlePause = () => {
    if (!accessToken || !restaurant) return;
    pauseRestaurant(accessToken, restaurant.id)
      .then((updatedRestaurant) => {
        setRestaurant(updatedRestaurant);
        setError(""); // Clear any previous error
      })
      .catch((e: Error) => {
        setError(e.message);
      });
  };

  return (
    <section className={className}>
      {error && (
        <p className="page__status page__status--error" role="alert">
          Error: {error}
        </p>
      )}

      {restaurant === null && !error && <p className="meta">Loading…</p>}

      {/* Once loaded: name, status, and the actions this role may use */}
      {restaurant && (
        <>
          <Heading level={3}>{restaurant.name}</Heading>
          <p className="meta">Status: {restaurant.status}</p>

          {restaurant.status === "OPEN" && (
            <Button onClick={handlePause}>Pause restaurant</Button>
          )}

          {restaurant.status === "PAUSED" && (
            <p className="meta">
              New orders are blocked until an operator reopens it.
            </p>
          )}
        </>
      )}
    </section>
  );
}
