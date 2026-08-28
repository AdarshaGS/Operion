import { ApiError } from "./client";
import { getSession } from "./tokenStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export interface AssetUploadResponse {
	reference: string;
	url: string;
}

/**
 * Separate from api/client.ts's request() - that helper always sends JSON, but a file
 * upload needs a multipart body with a browser-generated boundary (no explicit
 * Content-Type header, or the boundary is lost). Mirrors client.ts's own auth/error
 * handling otherwise.
 */
export async function uploadAsset(file: File): Promise<AssetUploadResponse> {
	const session = getSession();
	const headers = new Headers();
	headers.set("ngrok-skip-browser-warning", "true");
	if (session) {
		headers.set("Authorization", `Bearer ${session.token}`);
	}

	const formData = new FormData();
	formData.append("file", file);

	const response = await fetch(`${API_BASE_URL}/api/v1/assets`, { method: "POST", headers, body: formData });
	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new ApiError(response.status, body?.error ?? `Upload failed with status ${response.status}`);
	}
	return response.json() as Promise<AssetUploadResponse>;
}

/** Resolves a relative asset URL (e.g. "/uploads/xyz.png") to a fully-qualified one the
 * browser can load directly - the backend only ever returns relative paths since it
 * doesn't know its own public hostname. */
export function resolveAssetUrl(url: string): string {
	return `${API_BASE_URL}${url}`;
}
