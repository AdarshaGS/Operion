import { fetchWithSession, SESSION_EXPIRED_MESSAGE, type SessionAuth } from "./authFetch";
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

const tenantAuth: SessionAuth = {
	getToken: () => getSession()?.token ?? null,
	clearSession,
	expiredError: () => new ApiError(401, SESSION_EXPIRED_MESSAGE),
};

/**
 * Endpoints that run before a session exists (see JwtAuthenticationInterceptor.isPublic on the
 * backend). A 401 from one of these is AuthenticationFailedException ("invalid org/email/password"),
 * not an expired session, so no bearer token is sent and the 401 is never turned into a session
 * clear / redirect to login - the caller surfaces the backend's real message instead.
 */
const PUBLIC_AUTH_PATHS = new Set([
	"/api/v1/auth/login",
	"/api/v1/auth/refresh",
	"/api/v1/auth/claim-invite",
	"/api/v1/auth/claim-staff-invite",
	"/api/v1/auth/password-reset/request",
	"/api/v1/auth/password-reset/confirm",
	"/api/v1/auth/verify-email",
]);

export function isPublicAuthPath(path: string): boolean {
	return PUBLIC_AUTH_PATHS.has(path.split("?")[0]);
}

/**
 * The one fetch every tenant-plane API call goes through - JSON requests, downloads and the
 * multipart uploads in api/assets.ts, api/imports.ts, api/students.ts etc. Attaches the bearer
 * token and, on a 401 for a request that carried one, clears the session (which AuthContext
 * observes and answers by sending the user to /login) and throws ApiError(401). ApiExceptionHandler
 * on the backend never issues a 401 for a business rule (those come back as 404/409), so treating
 * it as "session is gone" here, rather than per-caller, is safe everywhere.
 *
 * Callers own the rest of the response handling. Does not catch network failures - request()
 * below does, for callers that want the friendly "couldn't reach the server" message.
 */
export function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
	const headers = new Headers(init.headers);
	// Harmless outside of ngrok - only skips ngrok's free-tier browser-warning
	// interstitial, which otherwise intercepts non-navigation requests (fetch/XHR) sent
	// to a fresh tunnel URL before a real page load has "clicked through" it once.
	headers.set("ngrok-skip-browser-warning", "true");
	return fetchWithSession(tenantAuth, `${API_BASE_URL}${path}`, { ...init, headers }, { public: isPublicAuthPath(path) });
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
	const headers = new Headers(options.headers);
	headers.set("Content-Type", "application/json");

	// fetch() only rejects when no HTTP response came back at all (server down, network drop,
	// request blocked by the browser). Wrap it so callers show a real reason instead of their
	// generic "Failed to ..." fallback, which hides that the request never reached the server.
	let response: Response;
	try {
		response = await apiFetch(path, { ...options, headers });
	} catch (err) {
		if (err instanceof ApiError) throw err;
		throw new ApiError(0, "Couldn't reach the Operion server - check that it is running and your connection is up, then try again");
	}

	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new ApiError(response.status, body?.error ?? `Request failed with status ${response.status}`);
	}

	if (response.status === 204) {
		return undefined as T;
	}
	// A void controller method answers 200 with no body rather than 204.
	const text = await response.text();
	return (text ? JSON.parse(text) : undefined) as T;
}

/** Fetches an authenticated file (PDF, CSV) and hands it to the browser as a download. */
export async function downloadFile(path: string, fallbackName: string): Promise<void> {
	const response = await apiFetch(path);
	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new ApiError(response.status, body?.error ?? `Download failed with status ${response.status}`);
	}
	const disposition = response.headers.get("Content-Disposition") ?? "";
	const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? fallbackName;
	const url = URL.createObjectURL(await response.blob());
	const link = document.createElement("a");
	link.href = url;
	link.download = name;
	link.click();
	URL.revokeObjectURL(url);
}

export const api = {
	get: <T>(path: string) => request<T>(path, { method: "GET" }),
	post: <T>(path: string, body?: unknown) => request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined }),
	patch: <T>(path: string, body?: unknown) => request<T>(path, { method: "PATCH", body: body ? JSON.stringify(body) : undefined }),
	put: <T>(path: string, body?: unknown) => request<T>(path, { method: "PUT", body: body ? JSON.stringify(body) : undefined }),
	delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};
