import {
    createContext,
    useContext,
    useState,
    type ReactNode,
} from "react";

export type LoginTokens = {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
};

export type CurrentUser = {
    id: string;
    firstName: string;
    lastName: string;
    username: string;
    role: "CUSTOMER" | "RESTAURANT" | "COURIER" | "OPERATOR";
};

type AuthContextType = {
    user: CurrentUser | null;
    accessToken: string | null;
    completeLogin: (tokens: LoginTokens) => Promise<void>;
    logout: () => void;
};

const AuthContext = createContext<AuthContextType | undefined>(
    undefined
);

export function AuthProvider({children}: { children: ReactNode }) {
    const [user, setUser] = useState<CurrentUser | null>(null);
    const [accessToken, setAccessToken] = useState<string | null>(null);

    async function completeLogin(tokens: LoginTokens) {
        const response = await fetch("http://localhost:8080/api/auth/me", {
            headers: {
                Authorization: `Bearer ${tokens.accessToken}`,
            },
        });

        if (!response.ok) {
            throw new Error("Could not retrieve authenticated user");
        }

        const profile: CurrentUser = await response.json();

        setAccessToken(tokens.accessToken);
        setUser(profile);
    }

    function logout() {
        setAccessToken(null);
        setUser(null);
    }

    return (
        <AuthContext.Provider
            value={{user, accessToken, completeLogin, logout}}
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
