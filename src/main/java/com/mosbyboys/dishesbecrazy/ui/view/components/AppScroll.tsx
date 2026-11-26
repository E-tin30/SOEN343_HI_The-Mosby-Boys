import type { ReactNode } from "react";
import { matchPath, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

// Pages that keep their own illustration instead of the role wallpaper.
const OWN_BACKGROUND_PATHS = ["/login", "/register"];

// The scrolling area above the output console (see #root in base.css). While someone is signed in,
// data-role gives it the wallpaper in their role's colour and a fade into the console at the bottom.
export default function AppScroll({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const ownBackground = OWN_BACKGROUND_PATHS.some((path) => matchPath(path, pathname));
  const role = user && !ownBackground ? user.role : undefined;

  return (
    <div className="app-scroll" data-role={role}>
      {children}
      {role && <div className="console-fade" aria-hidden="true" />}
    </div>
  );
}
