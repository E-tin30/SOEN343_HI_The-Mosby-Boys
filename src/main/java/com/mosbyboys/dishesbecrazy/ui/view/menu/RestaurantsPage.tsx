import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { getRestaurants } from "./api/menuApi";
import type { Restaurant } from "./types/menu";

export default function RestaurantsPage() {
  const { accessToken } = useAuth();
  const [restaurants, setRestaurants] = useState<Restaurant[] | null>(null); // null = still loading
  const [error, setError] = useState("");

  useEffect(() => {
    if (!accessToken) return;
    getRestaurants(accessToken)
      .then(setRestaurants)
      .catch((e: Error) => setError(e.message));
  }, [accessToken]);

  return (
    <main>
      <h1>Restaurants</h1>
      {error && <p>Error: {error}</p>}
      {!error && restaurants === null && <p>Loading…</p>}
      {restaurants?.length === 0 && <p>No restaurants to show.</p>}

      <ul>
        {restaurants?.map((r) => (
          <li key={r.id}>
            <Link to={`/restaurants/${r.id}/menu`}>{r.name}</Link> ({r.status})
          </li>
        ))}
      </ul>

      <Link to="/home">Back to home</Link>
    </main>
  );
}
