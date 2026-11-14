import { useNavigate } from "react-router-dom";
import { useAuth } from "./AuthContext";
import AppHeader from "./components/AppHeader";
import Button from "./components/Button";
import DemoResetControl from "./components/DemoResetControl";
import Heading from "./components/Heading";
import OperatorRestaurantList from "./components/OperatorRestaurantList"
import OperatorCourierList from "./components/OperatorCourierList";

export default function OperatorHomePage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="page">
      <AppHeader>
        {/* Only operators reach this page (ProtectedRoute); the control and the backend also check the role. */}
        <DemoResetControl key={user?.id} />
        <Button variant="secondary" onClick={handleLogout}>
          Log out
        </Button>
      </AppHeader>

      <main className="page__content">
        <section className="intro-card card">
          <p className="caption meta">Welcome back</p>
          <Heading>{`Hey ${user?.firstName ?? "there"}, ~everything running smoothly?~`}</Heading>
          <p className="intro-card__text meta">
            Keep an eye on restaurants, orders and deliveries across the city.
          </p>
          <hr className="intro-card__divider" />
          <div className="intro-card__actions">
            {/* Every order with its charge, plus Refund on delivered ones (R-DASH-01, R-PRC-04, R-PRC-05). */}
            <Button onClick={() => navigate("/orders/history")}>
              View all orders
            </Button>
            {/* Citywide live map of restaurants and couriers. */}
            <Button variant="secondary" onClick={() => navigate("/dashboard")}>
              View live map
            </Button>
          </div>
        </section>
        <OperatorRestaurantList />
        <OperatorCourierList />
      </main>
    </div>
  );
}
