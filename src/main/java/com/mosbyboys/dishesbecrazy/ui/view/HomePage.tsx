import { Link } from "react-router-dom";

export default function HomePage() {
  return (
    <main>
      <p><Link to="/restaurants">Browse restaurants</Link></p>

      <h1>Dishes Be Crazy</h1>
      <h2>Home Page</h2>

      <p>Login successful! Welcome to Dishes Be Crazy.</p>

      <hr />

      <h3>Development Placeholder</h3>
      <p>
        This is the starting point for integrating the
        application's features.
      </p>

      <p>
        Team members can add their pages and components here.
      </p>
    </main>
  );
}