
import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { log } from "./outputLog";

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
        const response = await fetch(`${API_URL}/api/auth/me`, {
          headers: {
            Authorization: `Bearer ${savedAccessToken}`,
          },
        });

        if (!response.ok) {
          throw new Error("Session expired or invalid");
        }

        const profile: CurrentUser = await response.json();

        setAccessToken(savedAccessToken);
        setUser(profile);
      } catch (error) {
        console.error("Could not restore session:", error);

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
    log("command", "GET /api/auth/me");

    const response = await fetch(`${API_URL}/api/auth/me`, {
      headers: {
        Authorization: `Bearer ${tokens.accessToken}`,
      },
    });

    if (!response.ok) {
      throw new Error("Could not retrieve authenticated user");
    }

    const profile: CurrentUser = await response.json();

    // Persist tokens across page reloads.
    localStorage.setItem("accessToken", tokens.accessToken);
    localStorage.setItem("refreshToken", tokens.refreshToken);

    setAccessToken(tokens.accessToken);
    setUser(profile);

    return profile;
  }

  function logout() {
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
