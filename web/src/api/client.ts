import { clearSession, getSession } from "./tokenStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

/**
 * The DTO interfaces in every other file under src/api/ are still hand-typed, not
 * generated from this client - they will drift from the real Java DTOs as modules
 * change. `npm run generate:api-types` (backend must be running on :8080) regenerates
 * ./generated-types.ts straight from springdoc's /v3/api-docs; diff it against a
 * hand-typed interface when something looks off, or adopt it directly in new code.
 */

/** Mirrors the backend's PageResponse<T> (com.operion.common.api.PageResponse) - the
 * standard envelope for any list endpoint that opts into pagination. */
export interface PageResponse<T> {
	content: T[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
}

export class ApiError extends Error {
	status: number;

	constructor(status: number, message: string) {
		super(message);
		this.status = status;
	}
}

/**
 * A 401 while a session was attached means the token was rejected (missing/expired) -
 * ApiExceptionHandler on the backend never issues one for a business rule (those come
 * back as 404/409), so treating it as "session is gone" and clearing it here (rather
 * than per-caller) is safe everywhere this client is used. But this same function also
 * carries the *login* request itself, which never has a session to attach in the first
 * place - a 401 there is AuthenticationFailedException ("invalid org/email/password"),
 * not an expired session, and must surface the backend's real message instead.
 */
async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
	const session = getSession();
	const headers = new Headers(options.headers);
	headers.set("Content-Type", "application/json");
	// Harmless outside of ngrok - only skips ngrok's free-tier browser-warning
	// interstitial, which otherwise intercepts non-navigation requests (fetch/XHR) sent
	// to a fresh tunnel URL before a real page load has "clicked through" it once.
	headers.set("ngrok-skip-browser-warning", "true");
	if (session) {
		headers.set("Authorization", `Bearer ${session.token}`);
	}

	const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });

	if (response.status === 401 && session) {
		clearSession();
		throw new ApiError(401, "Session expired - please log in again");
	}

	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new ApiError(response.status, body?.error ?? `Request failed with status ${response.status}`);
	}

	if (response.status === 204) {
		return undefined as T;
	}
	return response.json() as Promise<T>;
}

export const api = {
	get: <T>(path: string) => request<T>(path, { method: "GET" }),
	post: <T>(path: string, body?: unknown) => request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined }),
	patch: <T>(path: string, body?: unknown) => request<T>(path, { method: "PATCH", body: body ? JSON.stringify(body) : undefined }),
	put: <T>(path: string, body?: unknown) => request<T>(path, { method: "PUT", body: body ? JSON.stringify(body) : undefined }),
};
