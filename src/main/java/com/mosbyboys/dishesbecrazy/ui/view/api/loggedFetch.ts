import { log, logError } from "../outputLog";

export class RequestError extends Error {
  constructor(public readonly status: number) {
    super(status === 401 ? "Invalid credentials or expired session."
      : status === 403 ? "This action is not currently allowed."
      : `Request failed (${status}). Please refresh and try again.`);
  }
}
// Retains the HTTP status so pages can show the appropriate failure message.

// Only method/path/status are logged. Bodies and query strings can contain secrets.
export async function loggedFetch(url: string, options?: RequestInit): Promise<Response> {
  const action = `${options?.method ?? "GET"} ${new URL(url, window.location.origin).pathname}`;
  log("command", action);
  // Records the request path without exposing bodies, credentials, or query parameters.
  try {
    const response = await fetch(url, options);
    if (!response.ok) throw new RequestError(response.status);
    // Treats unsuccessful HTTP responses as failures even when fetch itself resolves.
    return response;
  } catch (error) {
    logError(error, `${action} failed${error instanceof RequestError ? ` (${error.status})` : ". Check your connection."}`);
    throw error;
    // Logs the failure once, then lets the calling page handle its own feedback.
  }
}

export async function readJson<T>(response: Response): Promise<T> {
  try {
    return await response.json() as T;
    // Parses JSON with the caller's expected TypeScript type; this is not runtime schema validation.
  } catch (error) {
    logError(error, "Could not read the server response.");
    // Makes malformed JSON visible in the output console before propagating the failure.
    throw error;
  }
}
