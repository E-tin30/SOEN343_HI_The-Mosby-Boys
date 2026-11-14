import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../AuthContext";
import { getHomeRoute } from "../roleRoutes";
import AppHeader from "../components/AppHeader";
import Button from "../components/Button";
import Heading from "../components/Heading";
import Tag from "../components/Tag";
import { getLedger } from "./api/ordersApi";
import type { LedgerEntry } from "./types/order";
import { STATUS_LABEL, STATUS_TONE } from "./orderStatus";
import "../styles/OrderHistoryPage.css";
import "../styles/LedgerPage.css";

const money = (n: number) => `${n < 0 ? "−" : ""}$${Math.abs(n).toFixed(2)}`;
const time = (iso: string) => new Date(iso).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" });

// Operator's view of the ledger: one row per charge and per refund (R-PRC-03, R-PRC-05)
export default function LedgerPage() {
    const navigate = useNavigate();
    const { user, accessToken } = useAuth();
    const [entries, setEntries] = useState<LedgerEntry[] | null>(null);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!accessToken) return;
        getLedger(accessToken)
            .then(setEntries)
            .catch((e: Error) => setError(e.message));
    }, [accessToken]);

    return (
        <div className="page">
            <AppHeader>
                <Button variant="secondary" onClick={() => navigate(user ? getHomeRoute(user.role) : "/login")}>
                    Back to home
                </Button>
            </AppHeader>

            <main className="history-board">
                <Heading level={2}>Ledger</Heading>

                {error && <p className="page__status page__status--error" role="alert">Error: {error}</p>}
                {!error && entries === null && <p className="page__status meta">Loading…</p>}
                {entries?.length === 0 && <p className="meta">No charges yet.</p>}

                {entries && entries.length > 0 && (
                    <section className="card ledger">
                        <table className="ledger__table">
                            <thead>
                                <tr>
                                    <th>Recorded</th>
                                    <th>Order</th>
                                    <th>Type</th>
                                    <th>Current status</th>
                                    <th className="ledger__amount">Amount</th>
                                </tr>
                            </thead>
                            <tbody>
                                {entries.map((e) => (
                                    <tr key={`${e.orderId}-${e.entryType}`}>
                                        <td className="ledger__nowrap">{time(e.recordedAt)}</td>
                                        <td>
                                            <strong>{e.restaurantName}</strong>
                                            <span className="meta ledger__sub">
                                                #{e.orderId.slice(-6)} · placed {time(e.placedAt)}
                                            </span>
                                        </td>
                                        <td>
                                            <Tag tone={e.entryType === "CHARGE" ? "sage" : "tomato"}>
                                                {e.entryType === "CHARGE" ? "Charge" : "Refund"}
                                            </Tag>
                                        </td>
                                        <td>
                                            <Tag tone={STATUS_TONE[e.orderStatus]}>{STATUS_LABEL[e.orderStatus]}</Tag>
                                        </td>
                                        <td className="ledger__amount">{money(e.amount)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </section>
                )}
            </main>
        </div>
    );
}
