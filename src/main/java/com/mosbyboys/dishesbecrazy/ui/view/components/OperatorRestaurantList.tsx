import { useState } from "react";
import { useAuth } from "../AuthContext";
import { openRestaurant, pauseRestaurant } from "../menu/api/menuApi";
import { useDashboardData } from "../dashboard/useDashboardData";
import { restaurantLoad } from "../dashboard/LiveMap";
import { STATUS_TAG } from "./RestaurantStatusControl";
import Button from "./Button";
import Heading from "./Heading";
import Tag from "./Tag";

const LOAD_TONE = {
    busy: "tomato",
    steady: "mustard",
    quiet: "sage"
} as const;

// Every restaurant with its live status and queue, and Pause/Reopen for each (R-DASH-01).
export default function OperatorRestaurantList() {
    const { accessToken } = useAuth();
    const { restaurants, error, refresh } = useDashboardData();
    const [pendingId, setPendingId] = useState<string | null>(null);   // row whose request is in flight
    const [actionError, setActionError] = useState("");

    const changeStatus = async (change: typeof pauseRestaurant, id: string) => {
        if (!accessToken) return;
        setPendingId(id);
        setActionError("");
        try {
            await change(accessToken, id);
            await refresh(); // show the new status now instead of on the next 5 s poll (R-DASH-06)
        } catch (e) {
            setActionError(e instanceof Error ? e.message : "Something went wrong.");
        } finally {
            setPendingId(null);
        }
    };

return (
    <section className="home-card card">
        <Heading level={3}>Restaurants</Heading>

        {(error || actionError) && (
            <p className="page__status page__status--error" role="alert">{actionError || error}</p>
        )}

        {restaurants.length === 0 ? (
            <p className="meta">No restaurants yet.</p>
        ) : (
            <ul className="page__list">
                {restaurants.map((r) => (
                    <li key={r.id} className="list-card">
                        <div className="list-card__info">
                            <strong>{r.name}</strong>
                            <Tag tone={STATUS_TAG[r.status].tone}>{STATUS_TAG[r.status].label}</Tag>
                            <Tag tone={LOAD_TONE[restaurantLoad(r)]}>{r.queueLength} queued</Tag>
                        </div>
                        <div className="list-card__side">
                            {r.status === "OPEN" ? (
                                <Button variant="secondary" disabled={pendingId !== null} onClick={() => changeStatus(pauseRestaurant, r.id)}>
                                    {pendingId === r.id ? "Saving…" : "Pause"}
                                </Button>
                            ) : (
                                <Button variant="secondary" disabled={pendingId !== null} onClick={() => changeStatus(openRestaurant, r.id)}>
                                    {pendingId === r.id ? "Saving…" : "Reopen"}
                                </Button>
                            )}

                        </div>
                    </li>
                ))}
            </ul>
        )}
    </section>
);
}
