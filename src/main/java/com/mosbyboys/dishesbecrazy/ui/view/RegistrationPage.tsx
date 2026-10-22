import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import Button from "./components/Button";
import Heading from "./components/Heading";
import Input from "./components/Input";
import TextLogo from "./components/TextLogo";
import "./styles/RegistrationPage.css";

export default function Register(){
    const navigate = useNavigate();
    const [role, setRole] = useState("CUSTOMER");
    async function handleSubmit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault();

        const data = Object.fromEntries(new FormData(e.currentTarget));

        try {
            const response = await fetch("http://localhost:8080/register", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify(data),
            });

            if (!response.ok) {
                throw new Error(`Registration failed: ${response.status}`);
            }

            const result = await response.text();
            alert(result);
            navigate("/login");
        } catch (error) {
            console.error("Registration error:", error);
            alert("Registration failed!");
        }
    }

    return (
        <main className="registration-page">
            <form className="registration-card card" onSubmit={handleSubmit}>
                <header className="registration-card__header">
                    <TextLogo layout="stacked" />
                    <Heading level={2} underlineText underlineAccent>
                        F*** dishes.  ~Get money.~
                    </Heading>
                    <p className="registration-card__subtitle meta">
                        Create an account to get started.
                    </p>
                </header>

                <div className="registration-card__fields">
                    <Input
                        label="First name"
                        name="firstName"
                        placeholder="First name"
                        autoComplete="given-name"
                        required
                    />
                    <Input
                        label="Last name"
                        name="lastName"
                        placeholder="Last name"
                        autoComplete="family-name"
                        required
                    />
                    <Input
                        label="Email"
                        type="email"
                        name="email"
                        placeholder="you@example.com"
                        autoComplete="email"
                        required
                    />
                    <Input
                        label="Username"
                        name="username"
                        placeholder="Username"
                        autoComplete="username"
                        required
                    />
                    <Input
                        label="Password"
                        type="password"
                        name="password"
                        placeholder="Password"
                        autoComplete="new-password"
                        required
                    />
                    <div className="field">
                        <label htmlFor="registration-role" className="field__label caption">
                            Role
                        </label>
                        <select
                            id="registration-role"
                            className="input registration-card__role"
                            name="role"
                            value={role}
                            onChange={(e) => setRole(e.target.value)} 
                            required
                        >
                            <option value="CUSTOMER">Customer</option>
                            <option value="RESTAURANT">Restaurant</option>
                            <option value="COURIER">Courier</option>
                        </select>
                    </div>
                    {role === "CUSTOMER" && (
                        <p><input name="address" placeholder="Delivery address" required /></p>
                    )}

                    {role === "RESTAURANT" && (
                        <>
                        <p><input name="restaurantName" placeholder="Restaurant name" required /></p>
                        <p><input name="address" placeholder="Restaurant address" required /></p>
                        </>
                    )}
                </div>

                <Button type="submit" block>
                    Create account
                </Button>

                <p className="registration-card__footer meta">
                    Already have an account?{" "}
                    <Button variant="link" onClick={() => navigate("/login")}>
                        Log in
                    </Button>
                </p>
            </form>
        </main>
    );
}
