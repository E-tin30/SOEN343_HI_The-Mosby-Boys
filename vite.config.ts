import { defineConfig } from "vite";

// npm run dev: the frontend calls /api/... on its own address, like the production image does.
// The dev server forwards those requests to the backend and removes the /api prefix.
export default defineConfig({
  server: {
    proxy: {
      "/api": {
        target: process.env.VITE_PROXY_TARGET ?? "http://localhost:8080",
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
});
