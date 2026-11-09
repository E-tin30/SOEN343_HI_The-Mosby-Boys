import { Component, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { APIProvider, Map, AdvancedMarker } from "@vis.gl/react-google-maps";
import Button from "../components/Button";
import { useDashboardData, type CourierData, type RestaurantDashboardData } from "./useDashboardData";
import "../styles/LiveMap.css";

const MONTREAL_BOUNDS = {
    north: 45.75,
    south: 45.35,
    east: -73.35,
    west: -74.10,
};

// Paused or 6+ queued is busy, 3-5 is getting there, anything less is quiet.
function restaurantLoad(restaurant: RestaurantDashboardData) {
    if (restaurant.status === "PAUSED" || restaurant.queueLength >= 6) return "busy";
    if (restaurant.queueLength >= 3) return "steady";
    return "quiet";
}

export const COURIER_LABEL: Record<CourierData["state"], string> = {
    IDLE: "Idle",
    ASSIGNED: "Assigned",
    CARRYING: "Carrying",
};

// If Google Maps fails (e.g. the API key's daily quota is used up), the markers throw while rendering.
// Without this the whole page goes blank; with it only the map is swapped for a message.
class MapErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
    state = { failed: false };

    static getDerivedStateFromError() {
        return { failed: true };
    }

    componentDidCatch(error: unknown) {
        console.error("The map failed to load:", error);
    }

    render() {
        if (this.state.failed) {
            return <p className="live-map__empty meta">The map couldn't load right now.</p>;
        }
        return this.props.children;
    }
}

type LiveMapProps = {
    restaurants: RestaurantDashboardData[];
    couriers: CourierData[];
    // Show each restaurant's name and queue above its pin
    showLabels?: boolean;
    // Called when a restaurant marker is hovered or clicked
    onSelectRestaurant?: (restaurant: RestaurantDashboardData) => void;
    // Called when empty map is clicked
    onMapClick?: () => void;
};

// The Montreal map with restaurant and courier markers. Fills its parent, so give the parent a height.
export default function LiveMap({
    restaurants,
    couriers,
    showLabels = false,
    onSelectRestaurant,
    onMapClick,
}: LiveMapProps) {
    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;

    if (!apiKey) {
        return <p className="live-map__empty meta">Google Maps API key is missing.</p>;
    }

    return (
        <MapErrorBoundary>
            <APIProvider apiKey={apiKey}>
                {/* Defining the base map settings */}
                <Map
                    className="live-map"
                    defaultCenter={{ lat: 45.52, lng: -73.70 }}
                    defaultZoom={10}
                    minZoom={8}
                    maxZoom={19}
                    mapId="648afd4657ed3697e408dbcb"
                    disableDefaultUI
                    zoomControl
                    onClick={onMapClick}
                    restriction={{
                        latLngBounds: MONTREAL_BOUNDS,
                        strictBounds: false,
                    }}
                >
                    {/* Restaurant pins, with the name and queue length stacked above when labels are on */}
                    {restaurants.map((restaurant) => {
                        const select = onSelectRestaurant && (() => onSelectRestaurant(restaurant));
                        return (
                            <AdvancedMarker
                                key={restaurant.id}
                                position={{
                                    lat: restaurant.latitude,
                                    lng: restaurant.longitude,
                                }}
                                title={`${restaurant.name} · ${restaurant.queueLength} queued`}
                                // Google Maps swallows DOM events on the marker's content, so listen on the marker itself.
                                // Its click also fires on Enter/Space, which covers keyboard users.
                                onMouseEnter={select}
                                onClick={select}
                            >
                                <div className={`restaurant-marker${select ? " restaurant-marker--interactive" : ""}`}>
                                    {showLabels && (
                                        <span className="restaurant-marker__label">
                                            {restaurant.name}
                                            <span className="meta"> · {restaurant.queueLength} queued</span>
                                        </span>
                                    )}
                                    <span className={`restaurant-marker__pin restaurant-marker__pin--${restaurantLoad(restaurant)}`} />
                                </div>
                            </AdvancedMarker>
                        );
                    })}

                    {/* Courier markers: shape and colour both follow the courier's state */}
                    {couriers.map((courier) => (
                        <AdvancedMarker
                            key={courier.id}
                            position={{
                                lat: courier.latitude,
                                lng: courier.longitude,
                            }}
                            title={`Courier · ${COURIER_LABEL[courier.state] ?? courier.state}`}
                        >
                            <span className={`courier-marker courier-marker--${courier.state.toLowerCase()}`} />
                        </AdvancedMarker>
                    ))}
                </Map>
            </APIProvider>
        </MapErrorBoundary>
    );
}

type MapCardProps = {
    // Whether there's something to track; when false, markers aren't loaded and the map is covered.
    active: boolean;
    // Shown over the map while inactive
    emptyMessage: string;
};

// The live map card for a home page's dashboard grid.
export function MapCard({ active, emptyMessage }: MapCardProps) {
    const navigate = useNavigate();
    const { restaurants, couriers } = useDashboardData(active);

    return (
        <section className="dashboard-card dashboard-card--map card">
            <div className="dashboard-card__map">
                <LiveMap restaurants={restaurants} couriers={couriers} />
                {!active && (
                    <div className="dashboard-card__map-overlay">
                        <p className="caption">{emptyMessage}</p>
                    </div>
                )}
            </div>
            <Button variant="link" className="dashboard-card__footer" onClick={() => navigate("/dashboard")}>
                View full map
            </Button>
        </section>
    );
}
