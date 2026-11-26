import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { useNotifications } from "./NotificationContext";
import "./NotificationBell.css";

function timeOf(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function BellIcon() {
  return (
    <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z" />
    </svg>
  );
}

export default function NotificationBell() {
  const { user } = useAuth();
  const { notifications, unreadCount, markAll } = useNotifications();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [open]);

  if (!user) return null;

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next && unreadCount > 0) markAll();
  };

  return (
    <div className="ntf-bell" ref={rootRef}>
      <button
        type="button"
        className="ntf-bell__button"
        aria-label={`Notifications${unreadCount ? `, ${unreadCount} unread` : ""}`}
        aria-expanded={open}
        onClick={toggle}
      >
        <BellIcon />
        {unreadCount > 0 && (
          <span className="ntf-bell__badge">{unreadCount > 9 ? "9+" : unreadCount}</span>
        )}
      </button>

      {open && (
        <div className="ntf-bell__panel" role="dialog" aria-label="Notifications">
          <div className="ntf-bell__header">Notifications</div>
          {notifications.length === 0 ? (
            <p className="ntf-bell__empty">Nothing yet. We'll tell you when your order moves.</p>
          ) : (
            <ul className="ntf-bell__list">
              {notifications.map((n) => {
                const content = (
                  <>
                    <span className="ntf-bell__dot" aria-hidden="true" />
                    <div className="ntf-bell__body">
                      <span className="ntf-bell__message">{n.message}</span>
                      <time className="ntf-bell__time" dateTime={n.createdAt}>
                        {timeOf(n.createdAt)}
                      </time>
                    </div>
                  </>
                );
                return (
                  <li key={n.id} className="ntf-bell__item">
                    {n.orderId ? (
                      <Link
                        className="ntf-bell__row ntf-bell__row--link"
                        to={`/orders/history#order-${n.orderId}`}
                        onClick={() => setOpen(false)}
                      >
                        {content}
                      </Link>
                    ) : (
                      <div className="ntf-bell__row">{content}</div>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}