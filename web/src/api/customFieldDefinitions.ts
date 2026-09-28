import { api } from "./client";

export type CustomFieldDataType = "TEXT" | "NUMBER" | "DATE" | "BOOLEAN" | "SELECT";

export interface CustomFieldDefinitionResponse {
	id: number;
	entityType: string;
	fieldKey: string;
	label: string;
	dataType: CustomFieldDataType;
	/** Comma-separated choices, only meaningful when dataType is SELECT. */
	options: string | null;
	required: boolean;
	status: "ACTIVE" | "ARCHIVED";
}

export interface CreateCustomFieldDefinitionRequest {
	entityType: string;
	fieldKey: string;
	label: string;
	dataType: CustomFieldDataType;
	options?: string | null;
	required: boolean;
}

export interface UpdateCustomFieldDefinitionRequest {
	label: string;
	dataType: CustomFieldDataType;
	options?: string | null;
	required: boolean;
}

export function listCustomFieldDefinitions(entityType?: string): Promise<CustomFieldDefinitionResponse[]> {
	const query = entityType ? `?entityType=${encodeURIComponent(entityType)}` : "";
	return api.get<CustomFieldDefinitionResponse[]>(`/api/v1/custom-field-definitions${query}`);
}

export function createCustomFieldDefinition(request: CreateCustomFieldDefinitionRequest): Promise<CustomFieldDefinitionResponse> {
	return api.post<CustomFieldDefinitionResponse>("/api/v1/custom-field-definitions", request);
}

export function updateCustomFieldDefinition(
	id: number,
	request: UpdateCustomFieldDefinitionRequest,
): Promise<CustomFieldDefinitionResponse> {
	return api.patch<CustomFieldDefinitionResponse>(`/api/v1/custom-field-definitions/${id}`, request);
}

export function changeCustomFieldDefinitionStatus(id: number, status: "ACTIVE" | "ARCHIVED"): Promise<CustomFieldDefinitionResponse> {
	return api.post<CustomFieldDefinitionResponse>(`/api/v1/custom-field-definitions/${id}/status`, { status });
}
