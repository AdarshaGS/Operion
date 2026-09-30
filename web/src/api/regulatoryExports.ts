import { ApiError, api } from "./client";
import { getSession } from "./tokenStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export type RegulatoryEntityType = "STUDENT" | "STAFF";

export interface RegulatoryExportFieldResponse {
	id: number;
	ordinal: number;
	columnHeader: string;
	sourceField: string;
	defaultValue: string | null;
}

export interface RegulatoryExportSchemaResponse {
	id: number;
	code: string;
	name: string;
	description: string | null;
	entityType: RegulatoryEntityType;
	status: "ACTIVE" | "INACTIVE";
	fields: RegulatoryExportFieldResponse[];
}

export interface RegulatoryExportSourceFieldResponse {
	key: string;
	label: string;
}

export interface RegulatoryExportFieldRequest {
	columnHeader: string;
	sourceField: string;
	defaultValue?: string | null;
}

export function listRegulatoryExportSchemas(): Promise<RegulatoryExportSchemaResponse[]> {
	return api.get<RegulatoryExportSchemaResponse[]>("/api/v1/regulatory-export-schemas");
}

export function listRegulatoryExportSourceFields(entityType: RegulatoryEntityType): Promise<RegulatoryExportSourceFieldResponse[]> {
	return api.get<RegulatoryExportSourceFieldResponse[]>(`/api/v1/regulatory-export-schemas/source-fields?entityType=${entityType}`);
}

export function createRegulatoryExportSchema(request: {
	code: string;
	name: string;
	description?: string | null;
	entityType: RegulatoryEntityType;
}): Promise<RegulatoryExportSchemaResponse> {
	return api.post<RegulatoryExportSchemaResponse>("/api/v1/regulatory-export-schemas", request);
}

export function updateRegulatoryExportSchema(
	id: number,
	request: { name: string; description?: string | null },
): Promise<RegulatoryExportSchemaResponse> {
	return api.patch<RegulatoryExportSchemaResponse>(`/api/v1/regulatory-export-schemas/${id}`, request);
}

export function replaceRegulatoryExportFields(
	id: number,
	fields: RegulatoryExportFieldRequest[],
): Promise<RegulatoryExportSchemaResponse> {
	return api.put<RegulatoryExportSchemaResponse>(`/api/v1/regulatory-export-schemas/${id}/fields`, fields);
}

export function changeRegulatoryExportStatus(id: number, status: "ACTIVE" | "INACTIVE"): Promise<RegulatoryExportSchemaResponse> {
	return api.post<RegulatoryExportSchemaResponse>(`/api/v1/regulatory-export-schemas/${id}/status`, { status });
}

/** Separate from api/client.ts's request() - the response is a CSV file, not JSON, so it has to be
 * read as a blob and handed to the browser as a download. */
export async function downloadRegulatoryExport(schema: Pick<RegulatoryExportSchemaResponse, "id" | "code">): Promise<void> {
	const session = getSession();
	const headers = new Headers();
	headers.set("ngrok-skip-browser-warning", "true");
	if (session) {
		headers.set("Authorization", `Bearer ${session.token}`);
	}
	const response = await fetch(`${API_BASE_URL}/api/v1/regulatory-export-schemas/${schema.id}/export`, { headers });
	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new ApiError(response.status, body?.error ?? `Export failed with status ${response.status}`);
	}
	const url = URL.createObjectURL(await response.blob());
	const link = document.createElement("a");
	link.href = url;
	link.download = `${schema.code}.csv`;
	link.click();
	URL.revokeObjectURL(url);
}
