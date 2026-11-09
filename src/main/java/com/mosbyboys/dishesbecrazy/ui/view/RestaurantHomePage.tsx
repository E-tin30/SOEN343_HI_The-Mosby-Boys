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
          <Heading>{`Hey ${user?.firstName ?? "there"}, ~ready to get cooking?~`}</Heading>
          <p className="home-card__text meta">
            Manage your menu and keep up with incoming orders.
          </p>
          <Button onClick={() => navigate("/restaurant/orders")}>
            View Orders
          </Button>
        </section>

        <RestaurantStatusControl className="home-card card" />

        <section className="home-card card">
          <Heading level={3}>Development placeholder</Heading>
          <p className="home-card__text meta">
            This is the starting point for restaurant features such as menu
            management and the order queue. Team members can add their pages
            and components here.
          </p>
        </section>
      </main>
    </div>
  );
}
