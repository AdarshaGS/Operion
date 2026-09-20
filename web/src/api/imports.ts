import { api, ApiError } from "./client";
import { getSession } from "./tokenStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export type ImportRowStatus = "VALID" | "IMPORTED" | "ERROR" | "DUPLICATE";

export interface ImportRowResult {
	row: number;
	status: ImportRowStatus;
	message: string;
	id: number | null;
}

export interface ImportRunResponse {
	id: number;
	entityType: string;
	fileName: string;
	totalRows: number;
	importedCount: number;
	errorCount: number;
	duplicateCount: number;
	createdAt: string;
}

/** Shared multipart upload for every XxxImportService's /import?validateOnly= endpoint -
 * same auth/error handling as api/client.ts's request(), but a file upload needs a
 * multipart body with a browser-generated boundary (see importStudents in api/students.ts,
 * left as its own copy since Students' backend predates this shared helper and has no
 * validateOnly param). */
export async function postImportFile(url: string, file: File, validateOnly: boolean): Promise<ImportRowResult[]> {
	const session = getSession();
	const headers = new Headers();
	headers.set("ngrok-skip-browser-warning", "true");
	if (session) {
		headers.set("Authorization", `Bearer ${session.token}`);
	}

	const formData = new FormData();
	formData.append("file", file);

	const response = await fetch(`${API_BASE_URL}${url}?validateOnly=${validateOnly}`, { method: "POST", headers, body: formData });
	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new ApiError(response.status, body?.error ?? `Import failed with status ${response.status}`);
	}
	return response.json() as Promise<ImportRowResult[]>;
}

export function fetchImportHistory(): Promise<ImportRunResponse[]> {
	return api.get<ImportRunResponse[]>("/api/v1/imports/history");
}
