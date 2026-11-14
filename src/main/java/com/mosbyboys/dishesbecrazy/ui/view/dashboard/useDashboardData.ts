import { useCallback, useEffect, useRef, useState } from "react";
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

const RESETTING = new Error("Demo reset in progress");

// GET one of the /api/dashboard endpoints as the signed-in user.
async function fetchDashboard<T>(path: string, token: string): Promise<T> {
    const response = await fetch(`http://localhost:8080/api/dashboard/${path}`, {
        headers: { Authorization: `Bearer ${token}` },
    });
    // 503 = a demo Reset is running; skip this refresh instead of showing an error
    if (response.status === 503) throw RESETTING;
    if (!response.ok) throw new Error(`Failed to load ${path}: ${response.status}`);
    return response.json();
}

// Fired by the Reset button once the demo data is restored.
export const DEMO_RESET_EVENT = "demo-reset";

// How often the map data is re-fetched so markers and queues stay live.
const REFRESH_INTERVAL_MS = 5000;

// Restaurants and online couriers for the map, refreshed every few seconds.
// Pass enabled = false to skip loading them.
export function useDashboardData(enabled = true) {
    const { accessToken } = useAuth();
    const [restaurants, setRestaurants] = useState<RestaurantDashboardData[]>([]);
    const [couriers, setCouriers] = useState<CourierData[]>([]);
    const [error, setError] = useState<string | null>(null);

    // false once the effect is cleaned up, so late responses get ignored
    const active = useRef(false);
    // bumped on every load(), so an older poll can't overwrite a newer refresh()
    const seq = useRef(0);

    // outside the effect so we can also return it as refresh()
    const load = useCallback(async () => {
        if (!accessToken || !enabled) return;
        const id = ++seq.current;
        const [restaurantsResult, couriersResult] = await Promise.allSettled([
            fetchDashboard<RestaurantDashboardData[]>("restaurants", accessToken),
            fetchDashboard<CourierData[]>("couriers", accessToken),
        ]);
        if (!active.current || id !== seq.current) return;
        if (restaurantsResult.status === "rejected" && restaurantsResult.reason === RESETTING) return;
        if (couriersResult.status === "rejected" && couriersResult.reason === RESETTING) return;
        if (restaurantsResult.status === "fulfilled") setRestaurants(restaurantsResult.value);
        else console.error("Error loading restaurants:", restaurantsResult.reason);
        if (couriersResult.status === "fulfilled") setCouriers(couriersResult.value);
        else console.error("Error loading couriers:", couriersResult.reason);
        // Keep the last good data on screen; just flag that the latest refresh failed.
        if (restaurantsResult.status === "rejected") setError("Couldn't load restaurants.");
        else if (couriersResult.status === "rejected") setError("Couldn't load couriers.");
        else setError(null);
    }, [accessToken, enabled]);

    useEffect(() => {
        if (!accessToken || !enabled) return;
        active.current = true;

        load();
        const interval = setInterval(() => {
            // Skip refreshes while the tab is in the background.
            if (document.visibilityState === "visible") load();
        }, REFRESH_INTERVAL_MS);

        // Catch up straight away when the user comes back to the tab.
        const onVisibilityChange = () => {
            if (document.visibilityState === "visible") load();
        };
        document.addEventListener("visibilitychange", onVisibilityChange);
        // Reload as soon as a demo Reset finishes instead of waiting for the next poll.
        window.addEventListener(DEMO_RESET_EVENT, load);

        return () => {
            active.current = false;
            clearInterval(interval);
            document.removeEventListener("visibilitychange", onVisibilityChange);
            window.removeEventListener(DEMO_RESET_EVENT, load);
        };
    }, [accessToken, enabled, load]);

    return { restaurants, couriers, error, refresh: load };
}
