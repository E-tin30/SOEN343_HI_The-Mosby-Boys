import { useState } from "react";
import { useAuth } from "../AuthContext";
import { getReassignCandidates, swapCourier, type IdleCourier } from "../orders/api/courierApi";
import { useDashboardData } from "../dashboard/useDashboardData";
import Button from "./Button";
import Heading from "./Heading";
import "./OperatorRestaurantList.css";

const shortId = (id: string) => `Courier ${id.slice(0, 8)}`;

// Operator hands an accepted, not-yet-picked-up order to another idle courier
export default function OperatorCourierList() {
    const { accessToken } = useAuth();
    const { couriers, error, refresh } = useDashboardData();
    const [openId, setOpenId] = useState<string | null>(null);
    const [candidates, setCandidates] = useState<IdleCourier[]>([]);
    const [choice, setChoice] = useState("");
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

    const startReassign = (courierId: string) =>
        run(async () => {
            if (!accessToken) return;
            const idle = await getReassignCandidates(accessToken, courierId);
            setCandidates(idle);
            setChoice(idle[0]?.id ?? "");
            setOpenId(courierId);
        });

    const confirm = (courierId: string) =>
        run(async () => {
            if (!accessToken || !choice) return;
            await swapCourier(accessToken, courierId, choice);
            setOpenId(null);
            await refresh();
        });

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
                    {assigned.map((c) => (
                        <li key={c.id} className="op-restaurants__row">
                            <span className="op-restaurants__dot" aria-hidden="true" />
                            <div className="op-restaurants__info">
                                <strong>{shortId(c.id)}</strong>
                                <span className="op-restaurants__meta">Not picked up yet</span>
                            </div>
                            {openId === c.id ? (
                                candidates.length === 0 ? (
                                    <>
                                        <span className="meta">No idle couriers</span>
                                        <Button variant="secondary" onClick={() => setOpenId(null)}>Cancel</Button>
                                    </>
                                ) : (
                                    <>
                                        <select
                                            aria-label="New courier"
                                            value={choice}
                                            onChange={(e) => setChoice(e.target.value)}
                                        >
                                            {candidates.map((idle) => (
                                                <option key={idle.id} value={idle.id}>{shortId(idle.id)}</option>
                                            ))}
                                        </select>
                                        <Button disabled={saving} onClick={() => confirm(c.id)}>
                                            {saving ? "Saving…" : "Confirm"}
                                        </Button>
                                        <Button variant="secondary" disabled={saving} onClick={() => setOpenId(null)}>
                                            Cancel
                                        </Button>
                                    </>
                                )
                            ) : (
                                <Button variant="secondary" disabled={saving} onClick={() => startReassign(c.id)}>
                                    Reassign
                                </Button>
                            )}
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
