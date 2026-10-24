import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import Tag from "../components/Tag";
import { getRestaurants } from "./api/menuApi";
import type { Restaurant } from "./types/menu";
import "../styles/RestaurantsPage.css";

const STATUS_TAG: Record<Restaurant["status"], { tone: "sage" | "mustard" | "neutral"; label: string }> = {
  OPEN: { tone: "sage", label: "Open" },
  PAUSED: { tone: "mustard", label: "Paused" },
  CLOSED: { tone: "neutral", label: "Closed" },
};

export default function RestaurantsPage() {
  const navigate = useNavigate();
  const { accessToken } = useAuth();
  const [restaurants, setRestaurants] = useState<Restaurant[] | null>(null);
  const [error, setError] = useState("");

    useEffect(() => {
        if (!accessToken) return;
        getRestaurants(accessToken)
            .then(setRestaurants)
            .catch((e: Error) => setError(e.message));
    }, [accessToken]);

  return (
    <div className="page">
      <AppHeader>
        <Button variant="secondary" onClick={() => navigate("/home")}>
          Back to home
        </Button>
      </AppHeader>

      <main className="page__content card">
        <header className="page__intro">
          <Heading>Pick a ~kitchen.~</Heading>
          <p className="meta">Choose a restaurant to see what's on the menu.</p>
        </header>

        {error && (
          <p className="page__status page__status--error" role="alert">
            Error: {error}
          </p>
        )}
        {!error && restaurants === null && (
          <p className="page__status meta">Loading…</p>
        )}
        {restaurants?.length === 0 && (
          <p className="page__status meta">No restaurants to show.</p>
        )}

        {restaurants && restaurants.length > 0 && (
          <ul className="page__list">
            {restaurants.map((r) => (
              <li key={r.id}>
                <Link to={`/restaurants/${r.id}/menu`} className="list-card restaurant-card">
                  <div className="list-card__info">
                    <Heading level={3}>{r.name}</Heading>
                    <span className="restaurant-card__cta">View menu →</span>
                  </div>
                  <Tag tone={STATUS_TAG[r.status].tone}>{STATUS_TAG[r.status].label}</Tag>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  );
}
