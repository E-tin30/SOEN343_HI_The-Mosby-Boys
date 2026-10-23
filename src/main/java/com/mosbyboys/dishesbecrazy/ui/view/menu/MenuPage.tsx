import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { useCart } from "../CartContext";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import Tag from "../components/Tag";
import { getMenu } from "./api/menuApi";
import type { MenuItem } from "./types/menu";
import "../styles/MenuPage.css";

export default function MenuPage() {
  const navigate = useNavigate();
  const { restaurantId } = useParams<{ restaurantId: string }>();
  const { accessToken, user } = useAuth();
  const { addItem, itemCount } = useCart();
  const [items, setItems] = useState<MenuItem[] | null>(null);
  const [error, setError] = useState("");

  const isCustomer = user?.role === "CUSTOMER";

  useEffect(() => {
    if (!accessToken || !restaurantId) return;
    getMenu(accessToken, restaurantId)
      .then(setItems)
      .catch((e: Error) => setError(e.message));
  }, [accessToken, restaurantId]);

  return (
    <div className="page">
      <AppHeader>
        {isCustomer && (
          <Button variant="secondary" onClick={() => navigate("/cart")}>
            View cart ({itemCount})
          </Button>
        )}
        <Button variant="secondary" onClick={() => navigate("/restaurants")}>
          Back to restaurants
        </Button>
      </AppHeader>

      <main className="page__content card">
        <header className="page__intro">
          <Heading>What's on the ~menu.~</Heading>
          <p className="meta">Everything this kitchen is cooking today.</p>
        </header>

        {error && (
          <p className="page__status page__status--error" role="alert">
            Error: {error}
          </p>
        )}
        {!error && items === null && <p className="page__status meta">Loading…</p>}
        {items?.length === 0 && (
          <p className="page__status meta">This restaurant has no menu items yet.</p>
        )}

        {items && items.length > 0 && (
          <ul className="page__list">
            {items.map((item) => {
              const soldOut = item.status === "SOLD_OUT";
              return (
                <li
                  key={item.id}
                  className={`list-card menu-item${soldOut ? " menu-item--sold-out" : ""}`}
                >
                  <div className="list-card__info">
                    <Heading level={3}>{item.name}</Heading>
                    {item.description && <p className="menu-item__description meta">{item.description}</p>}
                  </div>
                  <div className="menu-item__side">
                    <span className="menu-item__price">${item.price.toFixed(2)}</span>
                    {soldOut && <Tag>Sold out</Tag>}
                  </div>
                  {isCustomer && (
                    <Button
                      variant="secondary"
                      disabled={soldOut}
                      onClick={() => {
                        if (restaurantId) {
                          addItem(restaurantId, item);
                        }
                      }}
                    >
                      Add to cart
                    </Button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </main>
    </div>
  );
}
