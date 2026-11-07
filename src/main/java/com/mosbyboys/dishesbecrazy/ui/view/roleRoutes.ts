import type { Role } from "./AuthContext";

// Dashboard each role lands on after login.
// To add a dashboard for a new role (e.g. OPERATOR), add its path here
// and register the matching route in main.tsx.
const HOME_ROUTES: Partial<Record<Role, string>> = {
  CUSTOMER: "/home",
  RESTAURANT: "/restaurant/home",
  COURIER: "/courier/home",
};

// Roles without their own dashboard yet fall back to the customer home page.
const DEFAULT_HOME_ROUTE = "/home";

export function getHomeRoute(role: Role): string {
  return HOME_ROUTES[role] ?? DEFAULT_HOME_ROUTE;
}
