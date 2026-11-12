import { useState } from "react";
import { useAuth } from "../AuthContext";
import { openRestaurant, pauseRestaurant } from "../menu/api/menuApi";
import { useDashboardData } from "../dashboard/useDashboardData";
import { restaurantLoad } from "../dashboard/LiveMap";
import { STATUS_TAG } from "./RestaurantStatusControl";
import Button from "./Button";
import Heading from "./Heading";
import "./OperatorRestaurantList.css";

type Filter = "ALL" | "OPEN" | "PAUSED" | "CLOSED";
const FILTER_LABEL: Record<Filter, string> = { ALL: "All", OPEN: "Open", PAUSED: "Paused", CLOSED: "Closed" };

const SearchIcon = () => (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
        <circle cx="11" cy="11" r="7" />
        <path d="M20 20l-3.5-3.5" />
    </svg>
);
const AlertIcon = () => (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7.5v5.5M12 16.5v.01" />
    </svg>
);

const waitingText = (n: number) =>
    n === 0 ? "No orders waiting" : `${n} order${n === 1 ? "" : "s"} waiting`;

// Operator can pause/reopen any restaurant (R-DASH-01)
export default function OperatorRestaurantList() {
    const { accessToken } = useAuth();
    const { restaurants, error, refresh } = useDashboardData();
    const [pendingId, setPendingId] = useState<string | null>(null); // id of the row that's saving
    const [actionError, setActionError] = useState("");
    const [search, setSearch] = useState("");
    const [filter, setFilter] = useState<Filter>("ALL");

    const changeStatus = async (change: typeof pauseRestaurant, id: string) => {
        if (!accessToken) return;
        setPendingId(id);
        setActionError("");
        try {
            await change(accessToken, id);
            await refresh(); // don't wait for the 5s poll
        } catch (e) {
            setActionError(e instanceof Error ? e.message : "Something went wrong.");
        } finally {
            setPendingId(null);
        }
    };

    const count = (f: Filter) => restaurants.filter((r) => f === "ALL" || r.status === f).length;
    const shown = restaurants.filter(
        (r) =>
            (filter === "ALL" || r.status === filter) &&
            r.name.toLowerCase().includes(search.trim().toLowerCase())
    );

    return (
        <section className="card op-restaurants">
            <Heading level={3}>Restaurants</Heading>

            {(error || actionError) && (
                <p className="page__status page__status--error" role="alert">{actionError || error}</p>
            )}

            <div className="op-restaurants__toolbar">
                <label className="op-restaurants__search">
                    <SearchIcon />
                    <input
                        type="search"
                        placeholder="Search restaurants"
                        aria-label="Search restaurants"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                    />
                </label>
                <div className="op-restaurants__filters" role="group" aria-label="Filter by status">
                    {(Object.keys(FILTER_LABEL) as Filter[]).map((f) => (
                        <button
                            key={f}
                            type="button"
                            aria-pressed={filter === f}
                            className={`op-restaurants__filter${filter === f ? " op-restaurants__filter--active" : ""}`}
                            onClick={() => setFilter(f)}
                        >
                            {FILTER_LABEL[f]} <span>{count(f)}</span>
                        </button>
                    ))}
                </div>
            </div>

            {shown.length === 0 ? (
                <p className="meta">{restaurants.length === 0 ? "No restaurants yet." : "No restaurants match."}</p>
            ) : (
                <ul className="op-restaurants__list">
                    {shown.map((r) => {
                        // treat it as open here, otherwise every paused restaurant shows red (status already says paused)
                        const band = restaurantLoad({ ...r, status: "OPEN" });
                        return (
                            <li key={r.id} className={`op-restaurants__row op-restaurants__row--${r.status.toLowerCase()}`}>
                                <span className="op-restaurants__dot" aria-hidden="true" />
                                <div className="op-restaurants__info">
                                    <strong>{r.name}</strong>
                                    <span className="op-restaurants__meta">
                                        <span className="op-restaurants__status">{STATUS_TAG[r.status].label}</span>
                                        {" · "}
                                        <span className={`op-restaurants__queue op-restaurants__queue--${band}`}>
                                            {band !== "quiet" && <AlertIcon />}
                                            {waitingText(r.queueLength)}
                                        </span>
                                    </span>
                                </div>
                                {r.status === "OPEN" ? (
                                    <Button variant="secondary" disabled={pendingId !== null} onClick={() => changeStatus(pauseRestaurant, r.id)}>
                                        <span aria-hidden="true">⏸</span> {pendingId === r.id ? "Saving…" : "Pause"}
                                    </Button>
                                ) : (
                                    <Button
                                        variant="secondary"
                                        className="op-restaurants__reopen"
                                        disabled={pendingId !== null}
                                        onClick={() => changeStatus(openRestaurant, r.id)}
                                    >
                                        <span aria-hidden="true">▷</span> {pendingId === r.id ? "Saving…" : "Reopen"}
                                    </Button>
                                )}
                            </li>
                        );
                    })}
                </ul>
            )}
        </section>
    );
}
