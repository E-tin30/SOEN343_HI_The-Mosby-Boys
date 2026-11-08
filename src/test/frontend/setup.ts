import { afterEach, beforeEach, vi } from "vitest";
import { cleanup } from "@testing-library/react";

beforeEach(() => {
  vi.spyOn(console, "log").mockImplementation(() => {});
  vi.spyOn(console, "error").mockImplementation(() => {});
  Element.prototype.scrollIntoView = vi.fn();
});
// Silences developer-console noise and supplies scrolling behavior missing from the simulated DOM.
afterEach(() => { cleanup(); vi.useRealTimers(); });
// Removes rendered components and resets fake clocks so one test cannot affect the next.
