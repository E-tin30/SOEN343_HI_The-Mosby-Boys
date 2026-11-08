// R-DASH-06: every command sent to the backend and every error, shown in <OutputConsole>.
// Never log a request body: the login one holds the password.

export type LogEntry = { time: string; kind: "command" | "error"; text: string };

let entries: LogEntry[] = [];
const listeners = new Set<() => void>();

export function log(kind: LogEntry["kind"], text: string) {
  (kind === "error" ? console.error : console.log)(`[${kind}] ${text}`);
  entries = [...entries, { time: new Date().toLocaleTimeString(), kind, text }];
  listeners.forEach((l) => l());
}

export function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export const getEntries = () => entries;

const reportedErrors = new WeakSet<object>();

// Callers supply a safe description, never a raw server/SDK exception or payload.
export function logError(error: unknown, description: string) {
  if (typeof error === "object" && error !== null && "name" in error && error.name === "AbortError") return;
  if (typeof error === "object" && error !== null) {
    if (reportedErrors.has(error)) return;
    reportedErrors.add(error);
  }
  log("error", description);
}

export function installRuntimeLogging() {
  const onError = (event: ErrorEvent) => logError(event.error, "Unexpected application error.");
  const onRejection = (event: PromiseRejectionEvent) =>
    logError(event.reason, "An application action could not finish.");
  window.addEventListener("error", onError);
  window.addEventListener("unhandledrejection", onRejection);
  return () => {
    window.removeEventListener("error", onError);
    window.removeEventListener("unhandledrejection", onRejection);
  };
}
