import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { getMenu } from "./api/menuApi";
import type { MenuItem } from "./types/menu";

export default function MenuPage() {
  const { restaurantId } = useParams<{ restaurantId: string }>(); // from the URL /restaurants/:restaurantId/menu
  const { accessToken } = useAuth();
  const [items, setItems] = useState<MenuItem[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!accessToken || !restaurantId) return;
    getMenu(accessToken, restaurantId)
      .then(setItems)
      .catch((e: Error) => setError(e.message));
  }, [accessToken, restaurantId]);

  return (
    <main>
      <h1>Menu</h1>
      {error && <p>Error: {error}</p>}
      {!error && items === null && <p>Loading…</p>}
      {items?.length === 0 && <p>This restaurant has no menu items yet.</p>}

      {items && items.length > 0 && (
        <table border={1}>
          <thead>
            <tr><th>Dish</th><th>Description</th><th>Price</th><th>Status</th></tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id}>
                <td>{item.name}</td>
                <td>{item.description ?? ""}</td>
                <td>${item.price.toFixed(2)}</td>
                <td>{item.status === "SOLD_OUT" ? "SOLD OUT" : "Available"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <Link to="/restaurants">Back to restaurants</Link>
    </main>
  );
}
