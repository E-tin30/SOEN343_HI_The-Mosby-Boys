import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "jsdom",
    // The frontend calls the backend at VITE_API_URL (/api in the demo image); the tests assert absolute URLs.
    env: { VITE_API_URL: "http://localhost:8080" },
    include: ["src/test/frontend/**/*.test.{ts,tsx}"],
    restoreMocks: true,
    unstubGlobals: true,
    setupFiles: ["src/test/frontend/setup.ts"],
  },
  // Runs frontend tests in a simulated browser and restores mocks/globals between scenarios.
});
