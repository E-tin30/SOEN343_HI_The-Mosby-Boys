
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth, type LoginTokens } from "./AuthContext";

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

            // Retrieve the authenticated user's profile
            // and save it in the shared AuthContext.
            await completeLogin(loginTokens);

            // Navigate only after authentication is complete.
            navigate("/home", { replace: true });

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
        <form onSubmit={handleSubmit}>
            <h1>Login</h1>

            <input
                type="email"
                name="email"
                placeholder="Email"
                required
            />

            <input
                type="password"
                name="password"
                placeholder="Password"
                required
            />

            <button type="submit" disabled={loading}>
                {loading ? "Logging in..." : "Login"}
            </button>

            {error && <p role="alert">{error}</p>}

            <button
                type="button"
                onClick={() => navigate("/register")}
            >
                Register
            </button>
        </form>
    );
}
