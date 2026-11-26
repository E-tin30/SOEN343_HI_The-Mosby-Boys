import { useEffect, useRef, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { useNotifications } from "./NotificationContext";
import "./NotificationBell.css";

function timeOf(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
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
        <span aria-hidden="true">🔔</span>
        {unreadCount > 0 && <span className="ntf-bell__badge">{unreadCount}</span>}
      </button>

      {open && (
        <div className="ntf-bell__panel" role="dialog" aria-label="Notifications">
          {notifications.length === 0 ? (
            <p className="ntf-bell__empty">No notifications yet.</p>
          ) : (
            <ul className="ntf-bell__list">
              {notifications.map((n) => (
                <li key={n.id} className="ntf-bell__item">
                  <span className="ntf-bell__message">{n.message}</span>
                  <time className="ntf-bell__time" dateTime={n.createdAt}>{timeOf(n.createdAt)}</time>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}