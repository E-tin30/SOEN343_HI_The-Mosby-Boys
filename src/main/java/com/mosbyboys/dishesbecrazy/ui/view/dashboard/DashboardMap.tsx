import { APIProvider, Map } from "@vis.gl/react-google-maps";

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
        restriction={{
            latLngBounds: MONTREAL_BOUNDS,
            strictBounds: false,
        }}
        />
        </div>
        </APIProvider>
    );
}