import { STATUS_LABEL, STATUS_TONE } from "../orders/orderStatus";
import type { ActiveOrderSummary } from "../orders/types/order";
import Tag from "./Tag";
import "./ActiveOrderDetails.css";

type ActiveOrderDetailsProps = {
    order: ActiveOrderSummary;
};

export default function ActiveOrderDetails({ order }: ActiveOrderDetailsProps) {
    return (
        <>
            <Tag tone={STATUS_TONE[order.status]} className="active-order__status">
                {STATUS_LABEL[order.status]}
            </Tag>

            <div className="active-order__top">
                <span className="caption meta">{`Order #${order.id.slice(0, 6)}`}</span>
                <span className="active-order__restaurant">{order.restaurantName}</span>
            </div>

            <ul className="active-order__lines">
                {order.lines.map((line) => (
                    <li key={line.menuItemId}>
                        <span className="active-order__qty">{line.quantity}×</span> {line.menuItemName}
                    </li>
                ))}
            </ul>

            <div className="active-order__total">
                <span className="meta">Total</span>
                <span className="list-card__price">
                    {order.total === null ? "—" : `$${order.total.toFixed(2)}`}
                </span>
            </div>
        </>
    );
}
