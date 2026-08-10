import { clearSession, getSession } from "./tokenStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
	status: number;

	constructor(status: number, message: string) {
		super(message);
		this.status = status;
	}
}

/**
 * A 401 means the token is missing/expired - ApiExceptionHandler on the backend never
 * issues one for a business rule (those come back as 404/409), so treating it as
 * "session is gone" and clearing it here (rather than per-caller) is safe everywhere
 * this client is used.
 */
async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
	const session = getSession();
	const headers = new Headers(options.headers);
	headers.set("Content-Type", "application/json");
	if (session) {
		headers.set("Authorization", `Bearer ${session.token}`);
	}

	const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });

	if (response.status === 401) {
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
