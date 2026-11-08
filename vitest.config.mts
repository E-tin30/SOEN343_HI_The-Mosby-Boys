import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "jsdom",
    include: ["src/test/frontend/**/*.test.{ts,tsx}"],
    restoreMocks: true,
    unstubGlobals: true,
    setupFiles: ["src/test/frontend/setup.ts"],
  },
});
