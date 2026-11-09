import { useEffect, useState } from "react";
import { useAuth } from "../AuthContext";

export type RestaurantDashboardData = {
    id: string;
    name: string;
    status: "OPEN" | "PAUSED" | "CLOSED";
    latitude: number;
    longitude: number;
    queueLength: number;
};

export type CourierData = {
    id: string;
    state: "IDLE" | "ASSIGNED" | "CARRYING";
    latitude: number;
    longitude: number;
};

// GET one of the /api/dashboard endpoints as the signed-in user.
async function fetchDashboard<T>(path: string, token: string): Promise<T> {
    const response = await fetch(`http://localhost:8080/api/dashboard/${path}`, {
        headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok) throw new Error(`Failed to load ${path}: ${response.status}`);
    return response.json();
}

// Restaurants and online couriers for the map. Pass enabled = false to skip loading them.
export function useDashboardData(enabled = true) {
    const { accessToken } = useAuth();
    const [restaurants, setRestaurants] = useState<RestaurantDashboardData[]>([]);
    const [couriers, setCouriers] = useState<CourierData[]>([]);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!accessToken || !enabled) return;
        setError(null);

        fetchDashboard<RestaurantDashboardData[]>("restaurants", accessToken)
            .then(setRestaurants)
            .catch((e) => {
                console.error("Error loading restaurants:", e);
                setError("Couldn't load restaurants.");
            });

        fetchDashboard<CourierData[]>("couriers", accessToken)
            .then(setCouriers)
            .catch((e) => {
                console.error("Error loading couriers:", e);
                setError("Couldn't load couriers.");
            });
    }, [accessToken, enabled]);

    return { restaurants, couriers, error };
}
