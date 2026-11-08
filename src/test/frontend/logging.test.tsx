import { describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { loggedFetch, readJson, RequestError } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/api/loggedFetch";
import { getEntries, installRuntimeLogging, log, logError } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/outputLog";
import OutputConsole from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/OutputConsole";
import PageErrorBoundary from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/PageErrorBoundary";

const url = "http://localhost:8080/login?secret=private-query";

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

  it("keeps previous commands after remounting the console", () => {
    const view = render(<OutputConsole />);
    act(() => log("command", "Navigate to cart test"));
    view.unmount();
    render(<OutputConsole />);
    expect(screen.getByText(/Navigate to cart test/)).toBeTruthy();
  });

  it("captures runtime errors once and removes global handlers on cleanup", () => {
    const removeListener = vi.spyOn(window, "removeEventListener");
    const stop = installRuntimeLogging();
    const before = getEntries().length;
    window.dispatchEvent(new ErrorEvent("error", { error: new Error("private runtime details") }));
    stop();
    expect(removeListener).toHaveBeenCalledWith("error", expect.any(Function));
    expect(removeListener).toHaveBeenCalledWith("unhandledrejection", expect.any(Function));
    expect(getEntries().slice(before)).toEqual([expect.objectContaining({ kind: "error", text: "Unexpected application error." })]);
  });

  it("preserves the console when a page throws during rendering", () => {
    function Broken(): never { throw new Error("private render details"); }
    render(<><PageErrorBoundary><Broken /></PageErrorBoundary><OutputConsole /></>);
    expect(screen.getByRole("alert").textContent).toContain("could not load");
    expect(screen.getByText(/Could not render this page/)).toBeTruthy();
  });
});
