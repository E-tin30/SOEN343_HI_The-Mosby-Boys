import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import Button from "./components/Button";
import Heading from "./components/Heading";
import TextLogo from "./components/TextLogo";
import "./styles/HomePage.css";

export default function HomePage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="home-page">
      <header className="home-page__bar">
        <TextLogo />
        <Button variant="secondary" onClick={handleLogout}>
          Log out
        </Button>
      </header>

      <main className="home-page__content">
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
          <h3 className="home-card__title">Development placeholder</h3>
          <p className="home-card__text meta">
            This is the starting point for integrating the application's
            features. Team members can add their pages and components here.
          </p>
        </section>
      </main>
    </div>
  );
}
