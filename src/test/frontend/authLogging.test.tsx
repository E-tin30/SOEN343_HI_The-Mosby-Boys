import { afterEach, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { AuthProvider, useAuth } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/AuthContext";
import OutputConsole from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/OutputConsole";
import { getEntries } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/outputLog";

function Session() {
  const { user, loading, logout } = useAuth();
  return <><p>{loading ? "Restoring" : user ? "Signed in" : "Signed out"}</p><button onClick={logout}>Sign out test</button></>;
}
afterEach(() => localStorage.clear());

it("logs restored-session requests and local logout without exposing tokens", async () => {
  localStorage.setItem("accessToken", "private-token");
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: "customer", role: "CUSTOMER" }))));
  const before = getEntries().length;
  render(<AuthProvider><Session /><OutputConsole /></AuthProvider>);
  await screen.findByText("Signed in");
  expect(screen.getByText(/GET \/api\/auth\/me/)).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Sign out test" }));
  await screen.findByText("Signed out");
  expect(screen.getByText(/Log out/)).toBeTruthy();
  expect(localStorage.getItem("accessToken")).toBeNull();
  expect(JSON.stringify(getEntries().slice(before))).not.toContain("private-token");
});

it("reports a failed restoration exactly once and clears the invalid session", async () => {
  localStorage.setItem("accessToken", "expired-token");
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("private backend text", { status: 401 })));
  const before = getEntries().length;
  render(<AuthProvider><Session /><OutputConsole /></AuthProvider>);
  await screen.findByText("Signed out");
  expect(getEntries().slice(before).filter((e) => e.kind === "error")).toHaveLength(1);
  expect(localStorage.getItem("accessToken")).toBeNull();
});
