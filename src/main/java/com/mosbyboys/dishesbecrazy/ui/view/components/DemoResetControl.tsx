import { useRef, useState } from "react";
import { useAuth } from "../AuthContext";
import { loggedFetch, RequestError } from "../api/loggedFetch";
import { logError } from "../outputLog";
import Button from "./Button";
import "./DemoResetControl.css";

export default function DemoResetControl() {
  const { user, accessToken } = useAuth();
  const inFlight = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function handleReset() {
    if (!accessToken || user?.role !== "OPERATOR" || inFlight.current) return;
    inFlight.current = true;
    setPending(true);
    setError("");
    setMessage("");
    try {
      await loggedFetch("http://localhost:8080/api/demo/reset", {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      setMessage("Demo data restored.");
    } catch (failure) {
      logError(failure, "Demo reset failed. Retry Reset.");
      if (failure instanceof RequestError && [401, 403, 409].includes(failure.status)) {
        setError(failure.message);
      } else {
        setError("Reset did not complete. Retry Reset.");
      }
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }
  // Await backend restoration before success; retain the button for recovery retries and prevent duplicate submissions.

  if (user?.role !== "OPERATOR") return null;

  return (
    <div className="demo-reset" aria-busy={pending}>
      <Button variant="secondary" onClick={handleReset} disabled={pending || !accessToken}>
        {pending ? "Resetting…" : "Reset demo"}
      </Button>
      {message && <p className="demo-reset__feedback meta" role="status">{message}</p>}
      {error && <p className="demo-reset__feedback page__status--error" role="alert">{error}</p>}
    </div>
  );
}
// The backend enforces operator permission independently of this role-specific control.
