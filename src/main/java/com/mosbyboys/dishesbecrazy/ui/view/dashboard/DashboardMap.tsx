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
    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;

    if (!apiKey) {
        return <p>Google Maps API key is missing.</p>;
    }

    return (
        <APIProvider apiKey={apiKey}>
            <div style={{width: "90%", maxWidth: "1000px", height: "550px", margin: "0 auto", borderRadius: "12px",overflow: "hidden",}}>
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
                    <AdvancedMarker position={{ lat: 45.5019, lng: -73.5674 }} title="Zayden's Test Kitchen">
                        <Pin background="#16a34a" borderColor="#116c35" />
                    </AdvancedMarker>
                </Map>
            </div>
        </APIProvider>
    );
}