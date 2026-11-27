// Base URL of the backend. The production image serves the page and the API from one address:
// nginx forwards /api/... to the backend. For npm run dev, vite.config.ts proxies /api the same way.
// Set VITE_API_URL at build time only to point the frontend at a different backend.
export const API_BASE: string = import.meta.env.VITE_API_URL ?? "/api";
