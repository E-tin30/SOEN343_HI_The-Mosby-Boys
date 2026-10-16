import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";

export default function LoginPage() {
    const navigate = useNavigate();
    const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        const data = Object.fromEntries(new FormData(e.currentTarget));
        console.log("Login submitted for:", data.email);
    };

    return (
        <form onSubmit={handleSubmit}>
        <h1>Login</h1>

        <input type="email" name="email" placeholder="Email" required />
        <input type="password" name="password" placeholder="Password" required />

        <button type="submit">Login</button>
        <button type="button" onClick={() => navigate("/register")}>Register</button>
        </form>
    );
}