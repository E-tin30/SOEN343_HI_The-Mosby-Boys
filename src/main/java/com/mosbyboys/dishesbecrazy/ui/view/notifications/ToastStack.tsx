import { useNotifications } from "./NotificationContext";
import "./ToastStack.css";

export default function ToastStack() {
  const { toasts, dismissToast } = useNotifications();
  if (toasts.length === 0) return null;

  return (
    <div className="ntf-toasts" role="status" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className="ntf-toast">
          <span>{t.message}</span>
          <button type="button" aria-label="Dismiss notification" onClick={() => dismissToast(t.id)}>
            ×
          </button>
        </div>
      ))}
    </div>
  );
}