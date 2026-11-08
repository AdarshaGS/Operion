import { fetchWithSession, SESSION_EXPIRED_MESSAGE, type SessionAuth } from "../../api/authFetch";
import { clearPlatformSession, getPlatformSession } from "./platformTokenStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class PlatformApiError extends Error {
	status: number;

	constructor(status: number, message: string) {
		super(message);
		this.status = status;
	}
}

const platformAuth: SessionAuth = {
	getToken: () => getPlatformSession()?.token ?? null,
	clearSession: clearPlatformSession,
	expiredError: () => new PlatformApiError(401, SESSION_EXPIRED_MESSAGE),
};

/** The platform login runs before a session exists - a 401 there is "wrong email/password", not an
 * expired session, so it must not clear the session / redirect (which would loop). */
const PUBLIC_PLATFORM_PATHS = new Set(["/api/v1/platform/auth/login"]);

export function isPublicPlatformPath(path: string): boolean {
	return PUBLIC_PLATFORM_PATHS.has(path.split("?")[0]);
}

/** The one fetch every platform-plane API call goes through - shares the 401 handling in
 * api/authFetch.ts with the tenant client: clear the session (PlatformAuthContext observes it and
 * routes to /platform/login) and throw PlatformApiError(401). */
export function platformFetch(path: string, init: RequestInit = {}): Promise<Response> {
	return fetchWithSession(platformAuth, `${API_BASE_URL}${path}`, init, { public: isPublicPlatformPath(path) });
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
	const headers = new Headers(options.headers);
	headers.set("Content-Type", "application/json");

	const response = await platformFetch(path, { ...options, headers });

	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new PlatformApiError(response.status, body?.error ?? `Request failed with status ${response.status}`);
	}

	if (response.status === 204) {
		return undefined as T;
	}
	return response.json() as Promise<T>;
}

export const platformApi = {
	get: <T>(path: string) => request<T>(path, { method: "GET" }),
	post: <T>(path: string, body?: unknown) => request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined }),
	patch: <T>(path: string, body?: unknown) => request<T>(path, { method: "PATCH", body: body ? JSON.stringify(body) : undefined }),
	put: <T>(path: string, body?: unknown) => request<T>(path, { method: "PUT", body: body ? JSON.stringify(body) : undefined }),
	delete: <T = void>(path: string) => request<T>(path, { method: "DELETE" }),
};
