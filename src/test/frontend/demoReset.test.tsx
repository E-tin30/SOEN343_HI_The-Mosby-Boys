import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import type { CurrentUser, Role } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/AuthContext";
import HomePage from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/HomePage";
import OutputConsole from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/OutputConsole";
import { getEntries } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/outputLog";

const auth: { user: CurrentUser | null; accessToken: string | null; logout: () => void } = {
  user: null, accessToken: "private-reset-token", logout: vi.fn(),
};
vi.mock("../../main/java/com/mosbyboys/dishesbecrazy/ui/view/AuthContext", () => ({ useAuth: () => auth }));
vi.mock("../../main/java/com/mosbyboys/dishesbecrazy/ui/view/CartContext", () => ({ useCart: () => ({ itemCount: 0 }) }));
// HomePage loads order history on mount; stub it so the only fetch calls are the Reset button's.
vi.mock("../../main/java/com/mosbyboys/dishesbecrazy/ui/view/orders/api/ordersApi", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  getOrderHistory: () => Promise.resolve([]),
}));

beforeEach(() => {
  auth.user = { id: "operator", firstName: "Pat", lastName: "Demo", username: "demo_operator", role: "OPERATOR" };
  auth.accessToken = "private-reset-token";
});

function showHome() {
  return render(<MemoryRouter><HomePage /><OutputConsole /></MemoryRouter>);
}
// Render the real home page so role visibility and button wiring are verified together.

it.each<Role>(["CUSTOMER", "RESTAURANT", "COURIER"])("hides Reset from %s accounts", (role) => {
  auth.user = { ...auth.user!, role };
  showHome();
  expect(screen.queryByRole("button", { name: "Reset demo" })).toBeNull();
});

it("hides Reset when there is no authenticated profile", () => {
  auth.user = null;
  showHome();
  expect(screen.queryByRole("button", { name: "Reset demo" })).toBeNull();
});

it("awaits restoration, prevents duplicates and logs the command without exposing the token", async () => {
  let finish!: (response: Response) => void;
  const fetchMock = vi.fn(() => new Promise<Response>((resolve) => { finish = resolve; }));
  vi.stubGlobal("fetch", fetchMock);
  const before = getEntries().length;
  showHome();
  const button = screen.getByRole("button", { name: "Reset demo" });

  fireEvent.click(button);
  fireEvent.click(button);

  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(fetchMock).toHaveBeenCalledWith("http://localhost:8080/api/demo/reset", {
    method: "POST", headers: { Authorization: "Bearer private-reset-token" },
  });
  expect(button.hasAttribute("disabled")).toBe(true);
  expect(screen.queryByText("Demo data restored.")).toBeNull();
  await act(async () => finish(new Response(null, { status: 204 })));
  await screen.findByText("Demo data restored.");
  expect(screen.getByRole("button", { name: "Reset demo" }).hasAttribute("disabled")).toBe(false);
  expect(getEntries().slice(before).some((entry) => entry.text === "POST /api/demo/reset")).toBe(true);
  expect(JSON.stringify(getEntries().slice(before))).not.toContain("private-reset-token");
});

it.each([409, 503])("reports HTTP %s once and permits a successful retry", async (status) => {
  const reason = status === 409 ? "Reset timed out waiting for active requests. Retry Reset shortly." : "internal failure details";
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(reason, { status }))
    .mockResolvedValueOnce(new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetchMock);
  const before = getEntries().length;
  showHome();

  fireEvent.click(screen.getByRole("button", { name: "Reset demo" }));
  expect((await screen.findByRole("alert")).textContent).toContain("Retry Reset");
  if (status === 409) expect(screen.getByRole("alert").textContent).toBe(reason);
  expect(getEntries().slice(before).filter((entry) => entry.kind === "error")).toHaveLength(1);
  expect(JSON.stringify(getEntries().slice(before))).not.toContain("internal failure details");
  fireEvent.click(screen.getByRole("button", { name: "Reset demo" }));
  await screen.findByText("Demo data restored.");
  expect(screen.queryByRole("alert")).toBeNull();
  expect(fetchMock).toHaveBeenCalledTimes(2);
});

it("keeps network failures visible and leaves Reset available for retry", async () => {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Network unavailable")));
  showHome();
  fireEvent.click(screen.getByRole("button", { name: "Reset demo" }));
  await screen.findByRole("alert");
  expect(screen.queryByText("Demo data restored.")).toBeNull();
  expect(screen.getByRole("button", { name: "Reset demo" }).hasAttribute("disabled")).toBe(false);
});
