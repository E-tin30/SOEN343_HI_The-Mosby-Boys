import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth, type Role } from "./AuthContext";
import { getHomeRoute } from "./roleRoutes";
import PlateLogo from "./components/PlateLogo";
import "./styles/LoadingScreen.css";

type ProtectedRouteProps = {
  children: ReactNode;
  // When provided, only these roles may view the page; anyone else is sent to their own dashboard.
  allowedRoles?: Role[];
};

export default function ProtectedRoute({
  children,
  allowedRoles,
}: ProtectedRouteProps) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="loading-screen" role="status" aria-live="polite">
        <div className="loading-screen__spinner">
          <PlateLogo transparent />
        </div>
        <p className="caption loading-screen__text">Loading…</p>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to={getHomeRoute(user.role)} replace />;
  }

  return <>{children}</>;
}
