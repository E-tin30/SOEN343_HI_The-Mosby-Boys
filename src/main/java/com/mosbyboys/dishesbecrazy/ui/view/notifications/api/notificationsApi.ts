import { API_BASE } from "../../api/apiBase";

export interface AppNotification {
  id: string;
  kind: string;
  message: string;
  orderId: string | null;
  createdAt: string;
  read: boolean;
}

function headers(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export async function getNotifications(token: string): Promise<AppNotification[]> {
  const res = await fetch(`${API_BASE}/api/notifications`, { headers: headers(token) });
  if (!res.ok) throw new Error(`Notifications request failed (${res.status})`);
  return res.json();
}

export async function markAllRead(token: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/notifications/read`, {
    method: "POST",
    headers: headers(token),
  });
  if (!res.ok) throw new Error(`Mark-read request failed (${res.status})`);
}