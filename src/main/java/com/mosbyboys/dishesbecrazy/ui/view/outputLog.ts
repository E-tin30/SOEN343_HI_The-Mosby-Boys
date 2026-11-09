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
// Tracks reported error objects without keeping them alive indefinitely.

export function logError(error: unknown, description: string) {
  if (typeof error === "object" && error !== null && "name" in error && error.name === "AbortError") return;
  // Intentional cancellation of an obsolete request is not a user-facing failure.
  if (typeof error === "object" && error !== null) {
    if (reportedErrors.has(error)) return;
    reportedErrors.add(error);
  }
  // Prevents the same failure from being logged again as it passes through several callers.
  log("error", description);
  // Uses the existing on-screen log store with a safe description instead of a raw payload.
}
