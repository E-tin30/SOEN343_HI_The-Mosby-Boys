import { log, logError } from "../outputLog";

export class RequestError extends Error {
  constructor(public readonly status: number) {
    super(status === 401 ? "Invalid credentials or expired session."
      : status === 403 ? "This action is not currently allowed."
      : `Request failed (${status}). Please refresh and try again.`);
  }
}

// Only method/path/status are logged. Bodies and query strings can contain secrets.
export async function loggedFetch(url: string, options?: RequestInit): Promise<Response> {
  const action = `${options?.method ?? "GET"} ${new URL(url, window.location.origin).pathname}`;
  log("command", action);
  try {
    const response = await fetch(url, options);
    if (!response.ok) throw new RequestError(response.status);
    return response;
  } catch (error) {
    logError(error, `${action} failed${error instanceof RequestError ? ` (${error.status})` : ". Check your connection."}`);
    throw error;
  }
}

export async function readJson<T>(response: Response): Promise<T> {
  try {
    return await response.json() as T;
  } catch (error) {
    logError(error, "Could not read the server response.");
    throw error;
  }
}
