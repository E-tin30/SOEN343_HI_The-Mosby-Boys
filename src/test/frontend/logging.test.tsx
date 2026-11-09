import { describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { prepareOrder } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/orders/api/ordersApi";
import { loggedFetch, readJson, RequestError } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/api/loggedFetch";
import { getEntries, log, logError } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/outputLog";
import OutputConsole from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/OutputConsole";

const url = "http://localhost:8080/login?secret=private-query";
// Uses a fake secret in the URL to prove query parameters are excluded from output entries.

describe("application output logging", () => {
  it("shows commands and one HTTP error in the visible console without secrets", async () => {
    render(<OutputConsole />);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("private-response", { status: 401 })));
    const before = getEntries().length;
    let caught: unknown;
    await act(async () => {
      try { await loggedFetch(url, { method: "POST", headers: { Authorization: "private-token" }, body: "private-password" }); }
      catch (error) { caught = error; logError(error, "Login failed again"); }
    });
    expect(caught).toBeInstanceOf(RequestError);
    const entries = getEntries().slice(before);
    expect(entries.map((e) => e.kind)).toEqual(["command", "error"]);
    expect(entries[0].text).toBe("POST /login");
    expect(JSON.stringify(entries)).not.toMatch(/private-|failed again/);
    expect(screen.getByText(/POST \/login failed \(401\)/)).toBeTruthy();
  });
  // Verifies an HTTP failure appears once in the visible console without leaking request or response secrets.

  it("logs network and JSON failures once, and ignores deliberate cancellation", async () => {
    const before = getEntries().length;
    const error = new TypeError("private diagnostic");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(error));
    await expect(loggedFetch(url)).rejects.toBe(error);
    logError(error, "duplicate");
    expect(getEntries().slice(before).filter((e) => e.kind === "error")).toHaveLength(1);
    await expect(readJson(new Response("not json"))).rejects.toThrow();
    expect(getEntries().slice(before).filter((e) => e.kind === "error")).toHaveLength(2);
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new DOMException("aborted", "AbortError")));
    await expect(loggedFetch(url)).rejects.toThrow();
    expect(getEntries().slice(before).filter((e) => e.kind === "error")).toHaveLength(2);
  });
  // Covers network/JSON failures and confirms intentional cancellation does not add an error entry.

  it("keeps previous commands after remounting the console", () => {
    const view = render(<OutputConsole />);
    act(() => log("command", "Navigate to cart test"));
    view.unmount();
    render(<OutputConsole />);
    expect(screen.getByText(/Navigate to cart test/)).toBeTruthy();
  });
  // Checks that console entries survive route-like unmount/remount cycles.

  it("preserves a queue conflict reason for the page while logging only request metadata", async () => {
    const reason = "Order order-1 is not next in line";
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(reason, { status: 409 })));
    const before = getEntries().length;
    let caught: unknown;

    try {
      await prepareOrder("private-token", "order-1");
    } catch (error) {
      caught = error;
      logError(error, "duplicate queue failure");
    }

    expect(caught).toBeInstanceOf(RequestError);
    expect((caught as RequestError).status).toBe(409);
    expect((caught as Error).message).toBe(reason);
    const entries = getEntries().slice(before);
    expect(entries.map((entry) => entry.kind)).toEqual(["command", "error"]);
    expect(entries[1].text).toBe("POST /api/orders/order-1/prepare failed (409)");
    expect(JSON.stringify(entries)).not.toMatch(/not next in line|private-token|duplicate/);
  });
  // Exercises the real order helper so page feedback keeps the domain reason without exposing response bodies in logs.

  it.each([
    { path: "/api/orders/order-1/prepare", status: 400, contentType: "application/json" },
    { path: "/api/orders/order-1/prepare", status: 409, contentType: "text/html" },
    { path: "/api/orders/order-1/prepare", status: 500, contentType: "text/plain" },
    { path: "/payments/create-payment-intent", status: 400, contentType: "text/plain" },
  ])("uses generic feedback for $status $contentType errors from $path", async ({ path, status, contentType }) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("private diagnostic payload", {
      status,
      headers: { "Content-Type": contentType },
    })));
    const before = getEntries().length;

    await expect(loggedFetch(`http://localhost:8080${path}`)).rejects.toThrow(`Request failed (${status})`);
    expect(JSON.stringify(getEntries().slice(before))).not.toContain("private diagnostic payload");
  });
  // Limits propagated reasons to the order API's plain-text domain responses; other payloads retain safe fallback feedback.

});
