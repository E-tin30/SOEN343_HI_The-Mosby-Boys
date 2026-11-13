import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import AppHeader from "./components/AppHeader";
import Button from "./components/Button";
import Heading from "./components/Heading";
import RestaurantStatusControl from "./components/RestaurantStatusControl";
import "./styles/HomePage.css";

export default function RestaurantHomePage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  // The restaurant's own name, handed up by RestaurantStatusControl once it loads.
  const [restaurantName, setRestaurantName] = useState<string | null>(null);

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="page">
      <AppHeader>
        <Button variant="secondary" onClick={() => navigate("/orders/history")}>
          Order history
        </Button>
        <Button variant="secondary" onClick={handleLogout}>
          Log out
        </Button>
      </AppHeader>

      <main className="page__content">
        <section className="home-card card">
          <p className="caption meta">Welcome back</p>
          <Heading>{`Hey ${restaurantName ?? user?.firstName ?? "there"}, ~ready to get cooking?~`}</Heading>
          <p className="home-card__text meta">
            Manage your menu and keep up with incoming orders.
          </p>
          <div className="intro-card__actions">
            <Button onClick={() => navigate("/restaurant/orders")}>
              View Orders
            </Button>
            <Button variant="secondary" onClick={() => navigate("/restaurant/add")}>
              Add Menu Item
            </Button>
            <Button variant="secondary" onClick={() => navigate("/restaurant/edit")}>
              Edit Menu Item
            </Button>
          </div>
        </section>

        <RestaurantStatusControl onRestaurant={(r) => setRestaurantName(r.name)} />
      </main>
    </div>
  );
}
