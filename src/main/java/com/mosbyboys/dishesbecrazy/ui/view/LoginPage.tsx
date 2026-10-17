
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";

type LoginTokens = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
};

export default function LoginPage() {
    const [tokens, setTokens] = useState<LoginTokens | null>(null);
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();

        const formData = new FormData(e.currentTarget);
        const email = String(formData.get("email") ?? "");
        const password = String(formData.get("password") ?? "");

        setError("");
        setTokens(null);
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

        if (response.ok) {
            const loginTokens = await response.json();
            navigate("/home");
        }

        if (!response.ok) {
            setError("Login failed. Please try again.");
            return;
        }

        const loginTokens: LoginTokens = await response.json();
        setTokens(loginTokens);

        } catch {
        setError("Could not connect to the backend server.");
        } finally {
        setLoading(false);
        }
    };

    return (
        <form onSubmit={handleSubmit}>
        <h1>Login</h1>

        <input type="email" name="email" placeholder="Email" required />
        <input type="password" name="password" placeholder="Password" required />

        <button type="submit" disabled={loading}>
            {loading ? "Logging in..." : "Login"}
        </button>

        {error && <p role="alert">{error}</p>}
        {tokens && <p>Login successful!</p>}

        <button
            type="button"
            onClick={() => window.location.assign("/register")}
        >
            Register
        </button>
        </form>
    );
}