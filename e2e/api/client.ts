// Thin fetch wrapper for seeding test data directly against the backend, bypassing the
// UI. Mirrors web/src/api/client.ts's error shape, but takes an explicit bearer token
// per call instead of reading a single shared session - setup needs to act as several
// different identities (org owner, platform admin, newly-created staff) in one run.

export const API_BASE_URL = process.env.E2E_API_BASE_URL ?? "http://localhost:8090";

export class ApiError extends Error {
	status: number;

	constructor(status: number, message: string) {
		super(message);
		this.status = status;
	}
}

async function request<T>(path: string, options: { method?: string; body?: unknown; token?: string } = {}): Promise<T> {
	const headers: Record<string, string> = { "Content-Type": "application/json" };
	if (options.token) {
		headers.Authorization = `Bearer ${options.token}`;
	}

	const response = await fetch(`${API_BASE_URL}${path}`, {
		method: options.method ?? "GET",
		headers,
		body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
	});

	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new ApiError(response.status, body?.error ?? `Request failed with status ${response.status} for ${path}`);
	}

	if (response.status === 204) {
		return undefined as T;
	}
	return response.json() as Promise<T>;
}

export const api = {
	get: <T>(path: string, token?: string) => request<T>(path, { method: "GET", token }),
	post: <T>(path: string, body?: unknown, token?: string) => request<T>(path, { method: "POST", body, token }),
	patch: <T>(path: string, body?: unknown, token?: string) => request<T>(path, { method: "PATCH", body, token }),
	put: <T>(path: string, body?: unknown, token?: string) => request<T>(path, { method: "PUT", body, token }),
};
