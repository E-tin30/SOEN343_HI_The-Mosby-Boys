import { useState, type MouseEventHandler } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { openRestaurant, pauseRestaurant } from "../menu/api/menuApi";
import { RESTAURANT_STATUS_TAG } from "../menu/restaurantStatus";
import type { Restaurant } from "../menu/types/menu";
import Button from "./Button";
import Heading from "./Heading";
import Tag from "./Tag";
import "./RestaurantDetailsPanel.css";

type RestaurantDetailsPanelProps = {
    restaurant: Restaurant;
    // Number of orders in the restaurant's queue; null when it isn't known
    queueCount: number | null;
    // Called by the Close button, e.g. to clear the selected map marker
    onClose?: () => void;
    // Called after an operator pauses or reopens the restaurant, e.g. to reload the map data
    onStatusChange?: () => Promise<void> | void;
    // Pass useHoverPanel's panelProps here so the panel stays open while hovered
    onMouseEnter?: MouseEventHandler<HTMLElement>;
    onMouseLeave?: MouseEventHandler<HTMLElement>;
    className?: string;
};

// Details shown when a restaurant's map marker is hovered: status, order queue and quick actions
export default function RestaurantDetailsPanel({
    restaurant,
    queueCount,
    onClose,
    onStatusChange,
    onMouseEnter,
    onMouseLeave,
    className = "",
}: RestaurantDetailsPanelProps) {
    const navigate = useNavigate();
    const { user, accessToken } = useAuth();
    const status = RESTAURANT_STATUS_TAG[restaurant.status];
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    // The menu route is customer-only, so couriers and operators don't get the button
    const canOpenMenu = user?.role === "CUSTOMER";
    // Operators can pause or reopen any restaurant, same as from their home page's list (R-DASH-01)
    const canChangeStatus = user?.role === "OPERATOR";
    const isOpen = restaurant.status === "OPEN";

    const changeStatus = async () => {
        if (!accessToken || saving) return;
        setSaving(true);
        setError("");
        try {
            await (isOpen ? pauseRestaurant : openRestaurant)(accessToken, restaurant.id);
            // Wait for the reload so the button doesn't flash the old status
            await onStatusChange?.();
        } catch (e) {
            setError(e instanceof Error ? e.message : "Something went wrong.");
        } finally {
            setSaving(false);
        }
    };

    return (
        <section
            className={`restaurant-panel card ${className}`.trim()}
            aria-label={`${restaurant.name} details`}
            onMouseEnter={onMouseEnter}
            onMouseLeave={onMouseLeave}
        >
            <header className="restaurant-panel__header">
                <Heading level={3}>{restaurant.name}</Heading>
                {onClose && (
                    <Button variant="link" onClick={onClose}>
                        Close
                    </Button>
                )}
            </header>

            <Tag tone={status.tone} className="restaurant-panel__status">
                {status.label}
            </Tag>

            <div className="restaurant-panel__queue">
                <span className="caption meta">Order queue</span>
                <span className="restaurant-panel__queue-count">
                    {queueCount === null
                        ? "Queue unavailable"
                        : `${queueCount} ${queueCount === 1 ? "order" : "orders"}`}
                </span>
            </div>

            {canOpenMenu && (
                <Button block onClick={() => navigate(`/restaurants/${restaurant.id}/menu`)}>
                    Open menu
                </Button>
            )}

            {canChangeStatus && (
                <Button block onClick={changeStatus} disabled={saving}>
                    <span aria-hidden="true">{isOpen ? "⏸" : "▶"}</span>{" "}
                    {saving ? "Saving…" : isOpen ? "Pause orders" : "Reopen orders"}
                </Button>
            )}

            {error && (
                <p className="page__status page__status--error" role="alert">
                    {error}
                </p>
            )}
        </section>
    );
}
