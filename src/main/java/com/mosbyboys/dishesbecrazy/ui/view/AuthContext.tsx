import { loggedFetch, readJson } from "./api/loggedFetch";

import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { log, logError } from "./outputLog";

export type LoginTokens = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
};

export type Role =
    | "CUSTOMER"
    | "RESTAURANT"
    | "COURIER"
    | "OPERATOR";

export type CurrentUser = {
  id: string;
  firstName: string;
  lastName: string;
  username: string;
  role: Role;
};

type AuthContextType = {
  user: CurrentUser | null;
  accessToken: string | null;
  loading: boolean;
  completeLogin: (tokens: LoginTokens) => Promise<CurrentUser>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextType | undefined>(
    undefined
);

const API_URL = "http://localhost:8080";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Restore the session when the application starts.
  useEffect(() => {
    async function restoreSession() {
      const savedAccessToken = localStorage.getItem("accessToken");

      if (!savedAccessToken) {
        setLoading(false);
        return;
      }

      try {
        const response = await loggedFetch(`${API_URL}/api/auth/me`, {
          headers: {
            Authorization: `Bearer ${savedAccessToken}`,
          },
        });

        const profile: CurrentUser = await readJson<CurrentUser>(response);
        // Records restoration requests and unreadable responses without logging the saved token.

        setAccessToken(savedAccessToken);
        setUser(profile);
      } catch (error) {
        logError(error, "Could not restore session. Please sign in again.");
        // Sends restoration failures to the application console instead of only developer tools.

        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");

        setAccessToken(null);
        setUser(null);
      } finally {
        setLoading(false);
      }
    }

    restoreSession();
  }, []);

  async function completeLogin(tokens: LoginTokens): Promise<CurrentUser> {
    const response = await loggedFetch(`${API_URL}/api/auth/me`, {
      headers: {
        Authorization: `Bearer ${tokens.accessToken}`,
      },
    });

    const profile: CurrentUser = await readJson<CurrentUser>(response);
    // Uses shared request/JSON logging when retrieving the profile after a successful login.

    // Persist tokens across page reloads.
    localStorage.setItem("accessToken", tokens.accessToken);
    localStorage.setItem("refreshToken", tokens.refreshToken);

    setAccessToken(tokens.accessToken);
    setUser(profile);

    return profile;
  }

  function logout() {
    log("command", "Log out");
    // Records logout even though it is handled locally rather than by an HTTP request.
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");

    setAccessToken(null);
    setUser(null);
  }

  return (
      <AuthContext.Provider
          value={{
            user,
            accessToken,
            loading,
            completeLogin,
            logout,
          }}
      >
        {children}
      </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }

  return context;
}
