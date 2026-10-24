
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth, type LoginTokens } from "./AuthContext";
import { getHomeRoute } from "./roleRoutes";
import Button from "./components/Button";
import Heading from "./components/Heading";
import Input from "./components/Input";
import TextLogo from "./components/TextLogo";
import "./styles/LoginPage.css";

export default function LoginPage() {
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const navigate = useNavigate();
    const { completeLogin } = useAuth();

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();

        const formData = new FormData(e.currentTarget);
        const email = String(formData.get("email") ?? "");
        const password = String(formData.get("password") ?? "");

        setError("");
        setLoading(true);

        try {
            const response = await fetch("http://localhost:8080/login", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({ email, password }),
            });

            if (response.status === 401) {
                const message = await response.text();
                setError(message || "Invalid email or password");
                return;
            }

            if (!response.ok) {
                setError("Login failed. Please try again.");
                return;
            }

            const loginTokens: LoginTokens = await response.json();

            // Retrieve the authenticated user's profile and save it in the shared AuthContext.
            const profile = await completeLogin(loginTokens);

            // Navigate only after authentication is complete, to the dashboard that matches the account's role.
            navigate(getHomeRoute(profile.role), { replace: true });

        } catch (error) {
            console.error("Login error:", error);
            setError(
                "Could not complete login or load your profile. Please try again."
            );
        } finally {
            setLoading(false);
        }
    };

    return (
        <main className="login-page">
            <form className="login-card card" onSubmit={handleSubmit}>
                <header className="login-card__header">
                    <TextLogo layout="stacked" />
                    <Heading level={2}>Welcome ~back.~</Heading>
                    <p className="login-card__subtitle meta">
                        Log in to see what's cooking near you.
                    </p>
                </header>

                <div className="login-card__fields">
                    <Input
                        label="Email"
                        type="email"
                        name="email"
                        placeholder="you@example.com"
                        autoComplete="email"
                        required
                    />

                    <Input
                        label="Password"
                        type="password"
                        name="password"
                        placeholder="Password"
                        autoComplete="current-password"
                        required
                    />
                </div>

                {error && (
                    <p className="login-card__error" role="alert">
                        {error}
                    </p>
                )}

                <Button type="submit" block disabled={loading}>
                    {loading ? "Logging in..." : "Login"}
                </Button>

                <p className="login-card__footer meta">
                    New here?{" "}
                    <Button variant="link" onClick={() => navigate("/register")}>
                        Create an account
                    </Button>
                </p>
            </form>
        </main>
    );
}
