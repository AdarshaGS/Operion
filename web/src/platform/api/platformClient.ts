import { clearPlatformSession, getPlatformSession } from "./platformTokenStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class PlatformApiError extends Error {
	status: number;

	constructor(status: number, message: string) {
		super(message);
		this.status = status;
	}
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
	const session = getPlatformSession();
	const headers = new Headers(options.headers);
	headers.set("Content-Type", "application/json");
	if (session) {
		headers.set("Authorization", `Bearer ${session.token}`);
	}

	const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });

	if (response.status === 401 && session) {
		clearPlatformSession();
		throw new PlatformApiError(401, "Session expired - please log in again");
	}

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
};
