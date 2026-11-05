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
