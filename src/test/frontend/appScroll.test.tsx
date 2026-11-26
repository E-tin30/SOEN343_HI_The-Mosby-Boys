import { render } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import type { CurrentUser, Role } from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/context/AuthContext";
import AppScroll from "../../main/java/com/mosbyboys/dishesbecrazy/ui/view/components/AppScroll";

const auth: { user: CurrentUser | null } = { user: null };
vi.mock("../../main/java/com/mosbyboys/dishesbecrazy/ui/view/context/AuthContext", () => ({ useAuth: () => auth }));

beforeEach(() => {
  auth.user = null;
});

function showAt(path: string, role?: Role) {
  if (role) auth.user = { id: "u1", firstName: "Sam", lastName: "Demo", username: "demo_user", role };
  const { container } = render(
    <MemoryRouter initialEntries={[path]}><AppScroll><p>Page</p></AppScroll></MemoryRouter>
  );
  return container.querySelector(".app-scroll")!;
}
// The wallpaper and console fade are styled in base.css; these checks cover when AppScroll turns them on.

it.each<Role>(["CUSTOMER", "RESTAURANT", "COURIER", "OPERATOR"])("gives %s pages the role wallpaper and console fade", (role) => {
  const scroll = showAt("/dashboard", role);
  expect(scroll.getAttribute("data-role")).toBe(role);
  expect(scroll.lastElementChild?.classList.contains("console-fade")).toBe(true);
});

it.each(["/login", "/register", "/login/"])("leaves %s with its own illustration even when signed in", (path) => {
  const scroll = showAt(path, "RESTAURANT");
  expect(scroll.hasAttribute("data-role")).toBe(false);
  expect(scroll.querySelector(".console-fade")).toBeNull();
});

it("shows no wallpaper or fade when signed out", () => {
  const scroll = showAt("/dashboard");
  expect(scroll.hasAttribute("data-role")).toBe(false);
  expect(scroll.querySelector(".console-fade")).toBeNull();
});
