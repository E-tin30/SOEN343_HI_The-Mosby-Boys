import { log, logError } from "../outputLog";

function getDefaultErrorMessage(status: number): string {
  if (status === 401) {
    return "Invalid credentials or expired session.";
  }

  if (status === 403) {
    return "This action is not currently allowed.";
  }

  return `Request failed (${status}). Please refresh and try again.`;
}
// Keeps fallback feedback readable and consistent when no domain explanation can be shown.

export class RequestError extends Error {
  constructor(public readonly status: number, message = getDefaultErrorMessage(status)) {
    super(message);
  }
}
// Retains the HTTP status and user-facing explanation for the calling page.

async function readOrderErrorMessage(response: Response, path: string): Promise<string | undefined> {
  const isOrderRequest = path === "/placeOrder" || path.startsWith("/api/orders/");
  const isDomainError = [400, 403, 404, 409].includes(response.status);
  const isPlainText = response.headers.get("Content-Type")?.startsWith("text/plain");

  if (!isOrderRequest || !isDomainError || !isPlainText) {
    return undefined;
  }
  // ApiExceptionHandler returns plain-text order validation reasons; other endpoints/payloads use fallback feedback.

  try {
    const message = (await response.text()).trim();
    if (message && message.length <= 500) {
      return message;
    }
  } catch {
    return undefined;
  }
  // An unreadable, empty, or oversized body still produces an HTTP error, logged with its status by the caller.

  return undefined;
}
// Preserves concise order feedback for pages while keeping response bodies out of output entries.

export async function loggedFetch(url: string, options?: RequestInit): Promise<Response> {
  const path = new URL(url, window.location.origin).pathname;
  const action = `${options?.method ?? "GET"} ${path}`;
  log("command", action);
  // Records the request path without exposing bodies, credentials, or query parameters.

  try {
    const response = await fetch(url, options);
    if (!response.ok) {
      const message = await readOrderErrorMessage(response, path);
      throw new RequestError(response.status, message);
    }
    // Treats unsuccessful responses as failures and retains known order-validation feedback for the page.

    return response;
  } catch (error) {
    let failureMessage = `${action} failed. Check your connection.`;
    if (error instanceof RequestError) {
      failureMessage = `${action} failed (${error.status})`;
    }

    logError(error, failureMessage);
    throw error;
  }
}
// Logs each failure once using method/path/status, then lets the calling page handle its own feedback.

export async function readJson<T>(response: Response): Promise<T> {
  try {
    return await response.json() as T;
  } catch (error) {
    logError(error, "Could not read the server response.");
    throw error;
  }
}
// Logs malformed JSON before propagating it; the caller's TypeScript type does not validate the response shape.
