import { useEffect, useState } from "react";
import { useAuth } from "../AuthContext";
import { useDashboardData } from "../dashboard/useDashboardData";
import { restaurantLoad } from "../dashboard/LiveMap";

// Alerts the operator when ready orders wait for a courier (No Courier Available)
// or an open restaurant's queue is long (Dinner Rush), so they can act.
export default function OperatorAlerts() {
    const { accessToken } = useAuth();
    const { restaurants } = useDashboardData();
    const [waiting, setWaiting] = useState(0);

    useEffect(() => {
        if (!accessToken) return;
        const load = () =>
            fetch("http://localhost:8080/api/dashboard/dispatch-queue", {
                headers: { Authorization: `Bearer ${accessToken}` },
            })
                .then((r) => (r.ok ? r.json() : 0))
                .then(setWaiting)
                .catch(() => {}); // keep the last count if a poll fails
        load();
        const interval = setInterval(load, 5000);
        return () => clearInterval(interval);
    }, [accessToken]);

    const busy = restaurants.filter((r) => restaurantLoad(r) === "busy");
    if (waiting === 0 && busy.length === 0) return null;

    return (
        <section className="card" role="alert">
            {waiting > 0 && (
                <p className="page__status page__status--error">
                    {waiting} ready order{waiting === 1 ? " is" : "s are"} waiting for a courier.
                </p>
            )}
            {busy.map((r) => (
                <p key={r.id} className="page__status page__status--error">
                    {r.name} has {r.queueLength} orders queued. Consider pausing it.
                </p>
            ))}
        </section>
    );
}
