import { useState } from "react";
import { useAuth } from "../AuthContext";
import { getReassignCandidates, swapCourier, type IdleCourier } from "../orders/api/courierApi";
import { useDashboardData, type CourierData } from "../dashboard/useDashboardData";
import Button from "./Button";
import Heading from "./Heading";
import Tag from "./Tag";
import "./OperatorRestaurantList.css";
import "./OperatorCourierList.css";

const shortId = (id: string) => `Courier ID ${id.slice(0, 8)}`;

// Rough km between two points, good enough at city scale
const distanceKm = (a: IdleCourier, b: CourierData) => {
    const dLat = (a.latitude - b.latitude) * 111;
    const dLon = (a.longitude - b.longitude) * 111 * Math.cos((b.latitude * Math.PI) / 180);
    return Math.hypot(dLat, dLon);
};

const Avatar = ({ id }: { id: string }) => (
    <span className="op-couriers__avatar" aria-hidden="true">{id[0].toUpperCase()}</span>
);

// Operator hands an accepted, not-yet-picked-up order to another idle courier
export default function OperatorCourierList() {
    const { accessToken } = useAuth();
    const { couriers, error, refresh } = useDashboardData();
    const [openId, setOpenId] = useState<string | null>(null);
    const [candidates, setCandidates] = useState<IdleCourier[]>([]);
    const [saving, setSaving] = useState(false);
    const [actionError, setActionError] = useState("");

    const assigned = couriers.filter((c) => c.state === "ASSIGNED");

    const run = async (action: () => Promise<void>) => {
        setSaving(true);
        setActionError("");
        try {
            await action();
        } catch (e) {
            setActionError(e instanceof Error ? e.message : "Something went wrong.");
        } finally {
            setSaving(false);
        }
    };

    const open = (courier: CourierData) =>
        run(async () => {
            if (!accessToken) return;
            const idle = await getReassignCandidates(accessToken, courier.id);
            setCandidates(idle.sort((a, b) => distanceKm(a, courier) - distanceKm(b, courier)));
            setOpenId(courier.id);
        });

    const assign = (oldId: string, newId: string) =>
        run(async () => {
            if (!accessToken) return;
            await swapCourier(accessToken, oldId, newId);
            setOpenId(null);
            await refresh();
        });

    const option = (from: CourierData, idle: IdleCourier, suggested: boolean) => (
        <li key={idle.id} className={`op-couriers__option${suggested ? " op-couriers__option--suggested" : ""}`}>
            <Avatar id={idle.id} />
            <div className="op-restaurants__info">
                <span>
                    <strong>{shortId(idle.id)}</strong> {suggested && <Tag tone="sage">Closest</Tag>}
                </span>
                <span className="op-restaurants__meta">{distanceKm(idle, from).toFixed(1)} km from current courier</span>
            </div>
            <Button variant="secondary" disabled={saving} onClick={() => assign(from.id, idle.id)}>
                Assign
            </Button>
        </li>
    );

    return (
        <section className="card op-restaurants">
            <Heading level={3}>Assigned couriers</Heading>

            {(error || actionError) && (
                <p className="page__status page__status--error" role="alert">{actionError || error}</p>
            )}

            {assigned.length === 0 ? (
                <p className="meta">No couriers waiting to pick up.</p>
            ) : (
                <ul className="op-restaurants__list">
                    {assigned.map((c) => {
                        const isOpen = openId === c.id;
                        const [first, ...rest] = candidates;
                        return (
                            <li key={c.id} className="op-couriers__item">
                                <div className="op-restaurants__row">
                                    <Avatar id={c.id} />
                                    <div className="op-restaurants__info">
                                        <strong>{shortId(c.id)}</strong>
                                        <span className="op-restaurants__meta">Not picked up yet</span>
                                    </div>
                                    <Button
                                        variant="secondary"
                                        disabled={saving}
                                        aria-expanded={isOpen}
                                        onClick={() => (isOpen ? setOpenId(null) : open(c))}
                                    >
                                        {isOpen ? "Close" : "Reassign"}
                                    </Button>
                                </div>

                                {isOpen && (
                                    <div className="op-couriers__panel">
                                        {!first ? (
                                            <p className="meta">No idle couriers available.</p>
                                        ) : (
                                            <>
                                                <p className="caption meta">Suggested</p>
                                                <ul className="op-couriers__options">{option(c, first, true)}</ul>
                                                {rest.length > 0 && (
                                                    <>
                                                        <p className="caption meta">Other idle couriers</p>
                                                        <ul className="op-couriers__options">
                                                            {rest.map((idle) => option(c, idle, false))}
                                                        </ul>
                                                    </>
                                                )}
                                            </>
                                        )}
                                    </div>
                                )}
                            </li>
                        );
                    })}
                </ul>
            )}
        </section>
    );
}
