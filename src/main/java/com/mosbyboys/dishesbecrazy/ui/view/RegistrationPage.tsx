import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";

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
                "Content-Type": "application/json"
            },
            body: JSON.stringify(data)
        });

        if (!response.ok) {
            throw new Error(`Registration failed: ${response.status}`);
        }

        try {
            const response = await fetch("http://localhost:8080/register", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(data)
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
  }

  return (
    <form onSubmit={handleSubmit}>
      <h1>Register</h1>

      <p><input name="firstName" placeholder="First Name" required /></p>
      <p><input name="lastName" placeholder="Last Name" required /></p>
      <p><input name="email" type="email" placeholder="Email" required /></p>
      <p><input name="username" placeholder="Username" required /></p>
      <p><input name="password" type="password" placeholder="Password" required /></p>

      <p>
        <select
          name="role"
          value={role}
          onChange={(e) => setRole(e.target.value)}  
          required
        >
          <option value="CUSTOMER">Customer</option>
          <option value="RESTAURANT">Restaurant</option>
          <option value="COURIER">Courier</option>
        </select>
      </p>

      {role === "CUSTOMER" && (
        <p><input name="address" placeholder="Delivery address" required /></p>
      )}

      {role === "RESTAURANT" && (
        <>
          <p><input name="restaurantName" placeholder="Restaurant name" required /></p>
          <p><input name="address" placeholder="Restaurant address" required /></p>
        </>
      )}

      <button type="submit">Register</button>
    </form>
  );
}
