import type { FormEvent } from "react";

export default function Register() {
  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget));
    console.log(data);
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
        <select name="role" required>
          <option value="customer">Customer</option>
          <option value="restaurant">Restaurant</option>
          <option value="courier">Courier</option>
          <option value="operator">Operator</option>
        </select>
      </p>

      <button type="submit">Register</button>
    </form>
  );
}
