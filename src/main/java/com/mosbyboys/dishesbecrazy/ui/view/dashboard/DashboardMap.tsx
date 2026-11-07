import { APIProvider, Map, AdvancedMarker } from "@vis.gl/react-google-maps";
import { useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import Heading from "../components/Heading";
import RestaurantDetailsPanel from "../components/RestaurantDetailsPanel";
import Tag from "../components/Tag";
import { RESTAURANT_STATUS_TAG } from "../menu/restaurantStatus";

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

// Paused or 6+ queued is busy, 3-5 is getting there, anything less is quiet.
function restaurantLoad(restaurant: RestaurantDashboardData) {
    if (restaurant.status === "PAUSED" || restaurant.queueLength >= 6) return "busy";
    if (restaurant.queueLength >= 3) return "steady";
    return "quiet";
}

const COURIER_LABEL: Record<CourierData["state"], string> = {
    IDLE: "Idle",
    ASSIGNED: "Assigned",
    CARRYING: "Carrying",
};

export default function DashboardMap() {
    const { accessToken } = useAuth();
    const [restaurants, setRestaurants] = useState<RestaurantDashboardData[]>([]);
    const [couriers, setCouriers] = useState<CourierData[]>([]);
    const [showLabels, setShowLabels] = useState(true);
    const [error, setError] = useState<string | null>(null);
    // The restaurant shown in the side panel; stays until another marker is picked or the panel is closed.
    const [selected, setSelected] = useState<RestaurantDashboardData | null>(null);
    const close = () => setSelected(null);
    const sortedRestaurants = [...restaurants].sort((a, b) => a.name.localeCompare(b.name));
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
                setError("Couldn't load restaurants.");
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
                setError("Couldn't load couriers.");
            }
        }

        loadRestaurants();
        loadCouriers();
    }, [accessToken]);

    if (!apiKey) {
        return (
            <section className="dashboard-map card">
                <p className="dashboard-map__empty meta">Google Maps API key is missing.</p>
            </section>
        );
    }

    return (
        <APIProvider apiKey={apiKey}>
            <div className="dashboard-layout">
                <section className="dashboard-map card">
                    <div className="dashboard-map__toolbar">
                        <p className="caption meta">
                            {restaurants.length} restaurants · {couriers.length} couriers online
                        </p>
                        <label className="dashboard-map__toggle">
                            <input
                                type="checkbox"
                                checked={showLabels}
                                onChange={(e) => setShowLabels(e.target.checked)}
                            />
                            Show restaurant labels
                        </label>
                    </div>

                    {error && <p className="page__status page__status--error">{error}</p>}

                    <div className="dashboard-map__canvas">
                        {/* Defining the base map settings */}
                        <Map
                            defaultCenter={{ lat: 45.52, lng: -73.70 }}
                            defaultZoom={10}
                            minZoom={8}
                            maxZoom={19}
                            mapId="648afd4657ed3697e408dbcb"
                            disableDefaultUI
                            zoomControl
                            onClick={close}
                            restriction={{
                                latLngBounds: MONTREAL_BOUNDS,
                                strictBounds: false,
                            }}
                        >
                            {/* Restaurant pins, with the name and queue length stacked above when labels are on */}
                            {restaurants.map((restaurant) => (
                                <AdvancedMarker
                                    key={restaurant.id}
                                    position={{
                                        lat: restaurant.latitude,
                                        lng: restaurant.longitude,
                                    }}
                                    title={`${restaurant.name} · ${restaurant.queueLength} queued`}
                                    // Google Maps swallows DOM events on the marker's content, so listen on the marker itself.
                                    // Its click also fires on Enter/Space, which covers keyboard users.
                                    onMouseEnter={() => setSelected(restaurant)}
                                    onClick={() => setSelected(restaurant)}
                                >
                                    <div className="restaurant-marker">
                                        {showLabels && (
                                            <span className="restaurant-marker__label">
                                                {restaurant.name}
                                                <span className="meta"> · {restaurant.queueLength} queued</span>
                                            </span>
                                        )}
                                        <span className={`restaurant-marker__pin restaurant-marker__pin--${restaurantLoad(restaurant)}`} />
                                    </div>
                                </AdvancedMarker>
                            ))}

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
                    </div>

                    <div className="dashboard-map__legend">
                        <div className="dashboard-map__legend-group">
                            <span className="caption meta">Kitchens</span>
                            <span className="dashboard-map__legend-item">
                                <span className="legend-swatch legend-swatch--quiet" /> Quiet (0 – 2 orders)
                            </span>
                            <span className="dashboard-map__legend-item">
                                <span className="legend-swatch legend-swatch--steady" /> Steady (3 – 5 orders)
                            </span>
                            <span className="dashboard-map__legend-item">
                                <span className="legend-swatch legend-swatch--busy" /> Busy (6+ orders/Paused)
                            </span>
                        </div>
                        <div className="dashboard-map__legend-group">
                            <span className="caption meta">Couriers</span>
                            {(Object.keys(COURIER_LABEL) as CourierData["state"][]).map((state) => (
                                <span key={state} className="dashboard-map__legend-item">
                                    <span className={`courier-marker courier-marker--small courier-marker--${state.toLowerCase()}`} />
                                    {COURIER_LABEL[state]}
                                </span>
                            ))}
                        </div>
                    </div>
                </section>

                <div className="dashboard-layout__side">
                    {/* Every restaurant on the map; picking one shows its details below */}
                    <section className="dashboard-list card">
                        <Heading level={3}>{"On the ~map~"}</Heading>
                        {restaurants.length === 0 ? (
                            <p className="page__status meta">No restaurants to show.</p>
                        ) : (
                            <ul className="page__list dashboard-list__items">
                                {sortedRestaurants.map((restaurant) => (
                                    <li key={restaurant.id}>
                                        <button
                                            type="button"
                                            className={`list-card restaurant-card dashboard-list__item${
                                                selected?.id === restaurant.id ? " dashboard-list__item--selected" : ""
                                            }`}
                                            aria-pressed={selected?.id === restaurant.id}
                                            onClick={() => setSelected(restaurant)}
                                        >
                                            <div className="list-card__info">
                                                <span className="dashboard-list__name">{restaurant.name}</span>
                                                <span className="caption meta">{restaurant.queueLength} queued</span>
                                            </div>
                                            <Tag tone={RESTAURANT_STATUS_TAG[restaurant.status].tone}>
                                                {RESTAURANT_STATUS_TAG[restaurant.status].label}
                                            </Tag>
                                        </button>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </section>

                    {/* Details for the hovered or clicked restaurant, in their own card beside the map */}
                    {selected ? (
                        <RestaurantDetailsPanel
                            restaurant={selected}
                            queueCount={selected.queueLength}
                            onClose={close}
                        />
                    ) : (
                        <aside className="dashboard-layout__placeholder card">
                            <p className="meta">Hover over or click a restaurant to see its details.</p>
                        </aside>
                    )}
                </div>
            </div>
        </APIProvider>
    );
}
