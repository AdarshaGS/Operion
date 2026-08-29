export const FRONTEND_BASE_URL = process.env.E2E_FRONTEND_BASE_URL ?? "http://localhost:5183";
export const AUTH_DIR = new URL("./.auth/", import.meta.url).pathname;
