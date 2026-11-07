import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { getHomeRoute } from "../roleRoutes";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import DashboardMap from "./DashboardMap";
import "../styles/DashboardMap.css";

export default function DashboardPage() {
    const navigate = useNavigate();
    const { user } = useAuth();

    return (
        <div className="page">
            <AppHeader>
                <Button variant="secondary" onClick={() => navigate(user ? getHomeRoute(user.role) : "/login")}>
                    Back to home
                </Button>
            </AppHeader>

            <main className="page__content page__content--wide">
                <section className="intro-card card">
                    <p className="caption meta">Live dashboard</p>
                    <Heading>{"What's cooking in ~Montreal~"}</Heading>
                </section>

                <DashboardMap />
            </main>
        </div>
    );
}
