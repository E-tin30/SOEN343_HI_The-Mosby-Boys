import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { getHomeRoute } from "../roleRoutes";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import RestaurantDetailsPanel from "../components/RestaurantDetailsPanel";
import Tag from "../components/Tag";
import { RESTAURANT_STATUS_TAG } from "../menu/restaurantStatus";
import LiveMap, { COURIER_LABEL } from "./LiveMap";
import { useDashboardData, type CourierData, type RestaurantDashboardData } from "./useDashboardData";
import "../styles/DashboardMap.css";

export default function DashboardPage() {
    const navigate = useNavigate();
    const { user } = useAuth();
    const { restaurants, couriers, error } = useDashboardData();
    const [showLabels, setShowLabels] = useState(true);
    // The restaurant shown in the side panel; stays until another marker is picked or the panel is closed.
    const [selected, setSelected] = useState<RestaurantDashboardData | null>(null);
    const close = () => setSelected(null);
    const sortedRestaurants = [...restaurants].sort((a, b) => a.name.localeCompare(b.name));

    return (
        <div className="page">
            <AppHeader>
                <Button variant="secondary" onClick={() => navigate(user ? getHomeRoute(user.role) : "/login")}>
                    Back to home
                </Button>
            </AppHeader>

            <main className="page__content page__content--wide">
                <section className="intro-card card">
                    <p className="caption meta">Live dashboard</p>
                    <Heading>{"What's cooking in ~Montreal~"}</Heading>
                </section>

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
                            <LiveMap
                                restaurants={restaurants}
                                couriers={couriers}
                                showLabels={showLabels}
                                onSelectRestaurant={setSelected}
                                onMapClick={close}
                            />
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
            </main>
        </div>
    );
}
