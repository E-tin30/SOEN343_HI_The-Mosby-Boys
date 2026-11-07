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

const MONTREAL_BOUNDS = {
    north: 45.75,
    south: 45.35,
    east: -73.35,
    west: -74.10,
};

export default function DashboardMap() {
    const { accessToken } = useAuth();
    const [restaurants, setRestaurants] = useState<RestaurantDashboardData[]>([]);
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

        loadRestaurants();
    }, [accessToken]);


    if (!apiKey) {
        return <p>Google Maps API key is missing.</p>;
    }

    return (
        <APIProvider apiKey={apiKey}>
            <div style={{width: "90%", maxWidth: "1000px", height: "550px", margin: "0 auto", borderRadius: "12px",overflow: "hidden",}}>
                {/* Defining the base map settings*/}
                <Map
                defaultCenter={{ lat: 45.52, lng: -73.70 }}
                defaultZoom={10}
                minZoom={8}
                maxZoom={19}
                mapId="DEMO_MAP_ID"
                restriction={{
                    latLngBounds: MONTREAL_BOUNDS,
                    strictBounds: false,
                }}
                >
                {/* Creating the restaurent markers */}
                {restaurants.map((restaurant) => (
                    <AdvancedMarker key={restaurant.id} position={{lat: restaurant.latitude, lng: restaurant.longitude,}} title={restaurant.name}>
                        <Pin
                        background={
                            restaurant.status === "PAUSED" || restaurant.queueLength >= 6 ? "#dc2626": 
                            restaurant.queueLength >= 3 ? "#f59e0b": 
                            "#16a34a"
                        }
                        borderColor="#333333"
                        />
                    </AdvancedMarker>
                ))}

                </Map>
            </div>
        </APIProvider>
    );
}