import { api, apiFetch, ApiError } from "./client";

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
	const formData = new FormData();
	formData.append("file", file);

	const response = await apiFetch(`${url}?validateOnly=${validateOnly}`, { method: "POST", body: formData });
	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new ApiError(response.status, body?.error ?? `Import failed with status ${response.status}`);
	}
	return response.json() as Promise<ImportRowResult[]>;
}

export function fetchImportHistory(): Promise<ImportRunResponse[]> {
	return api.get<ImportRunResponse[]>("/api/v1/imports/history");
}
