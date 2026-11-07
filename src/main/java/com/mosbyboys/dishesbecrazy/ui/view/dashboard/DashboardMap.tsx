import { APIProvider, Map, AdvancedMarker, Pin } from "@vis.gl/react-google-maps";
import { useEffect, useState } from "react";
import { useAuth } from "../AuthContext";

type RestaurantDashboardData = {
    id: string;
    name: string;
    status: "OPEN" | "PAUSED" | "CLOSED";
    latitude: number;
    longitude: number;
    queueLength: number;
};

type CourierData = {
    id: string;
    state: "IDLE" | "ASSIGNED" | "CARRYING";
    latitude: number;
    longitude: number;
};

const MONTREAL_BOUNDS = {
    north: 45.75,
    south: 45.35,
    east: -73.35,
    west: -74.10,
};

export default function DashboardMap() {
    const { accessToken } = useAuth();
    const [restaurants, setRestaurants] = useState<RestaurantDashboardData[]>([]);
    const [couriers, setCouriers] = useState<CourierData[]>([]);
    const [showLabels, setShowLabels] = useState(true);
    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;

    useEffect(() => {
        if (!accessToken) return;

        async function loadRestaurants() {
            try {
                const response = await fetch(
                    //Fetching all the restaurants records from the backend
                    "http://localhost:8080/api/dashboard/restaurants",
                    {
                        headers: {
                            Authorization: `Bearer ${accessToken}`,
                        },
                    }
                );

                if (!response.ok) {
                    throw new Error(
                        `Failed to load restaurants: ${response.status}`
                    );
                }

                //Save the HTTP response as JavaScript data
                const data: RestaurantDashboardData[] = await response.json();

                //Updating the restaurants array to contain all the RestaurantDashboardData instances
                setRestaurants(data);
            } catch (error) {
                console.error("Error loading restaurants:", error);
            }
        }

        async function loadCouriers() {
            try {
                const response = await fetch(
                    //Fetching all the courier records from the backend
                    "http://localhost:8080/api/dashboard/couriers",
                    {
                        headers: {
                            Authorization: `Bearer ${accessToken}`,
                        },
                    }
                );

                if (!response.ok) {
                    throw new Error(
                        `Failed to load couriers: ${response.status}`
                    );
                }

                //Save the HTTP response as JavaScript data
                const data: CourierData[] = await response.json();

                //Updating the couriers array to contain all the CourierData instances
                setCouriers(data);
            } catch (error) {
                console.error("Error loading couriers:", error);
            }
        }

        loadRestaurants();
        loadCouriers()
    }, [accessToken]);

    if (!apiKey) {
        return <p>Google Maps API key is missing.</p>;
    }

    return (
        <APIProvider apiKey={apiKey}>
            <div
                style={{
                    width: "90%",
                    maxWidth: "1000px",
                    height: "550px",
                    margin: "0 auto",
                    borderRadius: "12px",
                    overflow: "hidden",
                }}
            >
                <label
                    style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "8px",
                        padding: "10px",
                        backgroundColor: "white",
                        color: "#333",
                    }}
                >
                    <input
                        type="checkbox"
                        checked={showLabels}
                        onChange={(e) => setShowLabels(e.target.checked)}
                    />
                    Show restaurant labels
                </label>
                {/* Defining the base map settings */}
                <Map
                    defaultCenter={{ lat: 45.52, lng: -73.70 }}
                    defaultZoom={10}
                    minZoom={8}
                    maxZoom={19}
                    mapId="648afd4657ed3697e408dbcb"
                    restriction={{
                        latLngBounds: MONTREAL_BOUNDS,
                        strictBounds: false,
                    }}
                >

                    {/* Creating the restaurant markers */}
                    {restaurants.map((restaurant) => (
                        <div key={restaurant.id}>
                            {/* Restaurant pin */}
                            <AdvancedMarker
                                position={{
                                    lat: restaurant.latitude,
                                    lng: restaurant.longitude,
                                }}
                                title={`${restaurant.name} · ${restaurant.queueLength} queued`}
                            >
                                <Pin
                                    background={
                                        restaurant.status === "PAUSED" ||
                                        restaurant.queueLength >= 6
                                            ? "#dc2626"
                                            : restaurant.queueLength >= 3
                                              ? "#f59e0b"
                                              : "#16a34a"
                                    }
                                    borderColor="#333333"
                                />
                            </AdvancedMarker>

                            {/* Separate restaurant label marker */}
                            {showLabels && (
                                <AdvancedMarker
                                    position={{
                                        lat: restaurant.latitude,
                                        lng: restaurant.longitude,
                                    }}
                                    zIndex={10}
                                >
                                    <div
                                        style={{
                                            position: "relative",
                                            transform: "translateY(-48px)",
                                            backgroundColor: "white",
                                            color: "#333333",
                                            padding: "5px 8px",
                                            borderRadius: "6px",
                                            fontSize: "12px",
                                            fontWeight: 600,
                                            whiteSpace: "nowrap",
                                            boxShadow: "0 2px 6px rgba(0,0,0,0.3)",
                                            pointerEvents: "none",
                                        }}
                                    >
                                        {restaurant.name} · {restaurant.queueLength} queued
                                    </div>
                                </AdvancedMarker>
                            )}
                        </div>
                    ))}

                    {/* Setting the courier marker styles */}
                    {couriers.map((courier) => {
                        const styles: Record<string, React.CSSProperties> = {
                            IDLE: {
                                // Green circle
                                backgroundColor: "#16a34a",
                                borderRadius: "50%",
                            },
                            ASSIGNED: {
                                // Yellow diamond
                                backgroundColor: "#f59e0b",
                                borderRadius: "4px",
                            },
                            CARRYING: {
                                // Blue diamond
                                backgroundColor: "#2563eb",
                                borderRadius: "4px",
                                transform: "rotate(45deg)",
                            },
                        };

                        return (
                            // Placing the courier markers
                            <AdvancedMarker
                                key={courier.id}
                                position={{
                                    lat: courier.latitude,
                                    lng: courier.longitude,
                                }}
                                title={`Courier - ${courier.state}`}
                            >
                                <div
                                    style={{
                                        width: "30px",
                                        height: "30px",
                                        border: "2px solid white",
                                        boxShadow: "0 2px 6px rgba(0,0,0,0.3)",
                                        ...styles[courier.state],
                                    }}
                                />
                            </AdvancedMarker>
                        );
                    })}
                </Map>
            </div>
        </APIProvider>
    );
}
