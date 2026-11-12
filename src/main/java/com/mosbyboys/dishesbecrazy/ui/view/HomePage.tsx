import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import AppHeader from "./components/AppHeader";
import Button from "./components/Button";
import Heading from "./components/Heading";
import DemoResetControl from "./components/DemoResetControl";
import "./styles/HomePage.css";

export default function HomePage() {
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
        {user?.role === "OPERATOR" && <DemoResetControl key={user.id} />}
        {/* Keep the operator's demo reset beside session actions without occupying a dashboard card. */}
      </AppHeader>

      <main className="page__content">
        <section className="home-card card">
          <p className="caption meta">Welcome back</p>
          <Heading>{`Hey ${user?.firstName ?? "there"}, ~hungry?~`}</Heading>
          <p className="home-card__text meta">
            Find home cooks and restaurants near you.
          </p>
          <Button onClick={() => navigate("/restaurants")}>
            Browse restaurants
          </Button>
        </section>

        <section className="home-card card">
          <Heading level={3}>Development placeholder</Heading>
          <p className="home-card__text meta">
            This is the starting point for integrating the application's
            features. Team members can add their pages and components here.
          </p>
        </section>
      </main>
    </div>
  );
}
