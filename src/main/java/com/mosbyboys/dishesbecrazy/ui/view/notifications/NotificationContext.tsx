import {
  createContext, useCallback, useContext, useEffect, useMemo, useRef, useState,
  type ReactNode,
} from "react";
import { useAuth } from "../context/AuthContext";
import {
  getNotifications, markAllRead as postMarkAllRead, type AppNotification,
} from "./api/notificationsApi";

const POLL_MS = 3000;
const TOAST_MS = 5000;

interface NotificationContextValue {
  notifications: AppNotification[];
  unreadCount: number;
  toasts: AppNotification[];
  dismissToast: (id: string) => void;
  markAll: () => void;
}

const EMPTY: NotificationContextValue = {
  notifications: [],
  unreadCount: 0,
  toasts: [],
  dismissToast: () => {},
  markAll: () => {},
};

const NotificationContext = createContext<NotificationContextValue>(EMPTY);

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { accessToken } = useAuth();
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [toasts, setToasts] = useState<AppNotification[]>([]);
  const seenIds = useRef<Set<string>>(new Set());
  const firstFetch = useRef(true);
  const timers = useRef<number[]>([]);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  useEffect(() => {
    // new session (login, logout, switch user): start clean
    seenIds.current = new Set();
    firstFetch.current = true;
    setNotifications([]);
    setToasts([]);
    if (!accessToken) return;

    let cancelled = false;

    const poll = async () => {
      try {
        const list = await getNotifications(accessToken);
        if (cancelled) return;
        setNotifications(list);

        // The first fetch only seeds what already exists, so logging in
        // does not replay old notifications as toasts.
        const fresh = firstFetch.current
          ? []
          : list.filter((n) => !seenIds.current.has(n.id) && !n.read);
        list.forEach((n) => seenIds.current.add(n.id));
        firstFetch.current = false;

        if (fresh.length > 0) {
          setToasts((prev) => [...fresh, ...prev]);
          fresh.forEach((n) => {
            timers.current.push(window.setTimeout(() => dismissToast(n.id), TOAST_MS));
          });
        }
      } catch {
        // backend briefly unavailable: try again on the next tick
      }
    };

    poll();
    const interval = window.setInterval(poll, POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
      timers.current.forEach((t) => window.clearTimeout(t));
      timers.current = [];
    };
  }, [accessToken, dismissToast]);

  const markAll = useCallback(() => {
    if (!accessToken) return;
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    postMarkAllRead(accessToken).catch(() => {});
  }, [accessToken]);

  const value = useMemo<NotificationContextValue>(
    () => ({
      notifications,
      unreadCount: notifications.filter((n) => !n.read).length,
      toasts,
      dismissToast,
      markAll,
    }),
    [notifications, toasts, dismissToast, markAll],
  );

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
}

export function useNotifications(): NotificationContextValue {
  return useContext(NotificationContext);
}