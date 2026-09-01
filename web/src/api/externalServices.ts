import { api } from "./client";

export interface ExternalServicePropertyStatusResponse {
	key: string;
	secret: boolean;
	configured: boolean;
}

export interface ExternalServiceSettingsResponse {
	serviceKey: string;
	displayName: string;
	enabled: boolean;
	properties: ExternalServicePropertyStatusResponse[];
}

export function listExternalServices(): Promise<ExternalServiceSettingsResponse[]> {
	return api.get<ExternalServiceSettingsResponse[]>("/api/v1/organisation/external-services");
}

export function updateExternalServiceProperties(
	serviceKey: string,
	properties: Record<string, string>,
): Promise<ExternalServiceSettingsResponse> {
	return api.put<ExternalServiceSettingsResponse>(`/api/v1/organisation/external-services/${serviceKey}/properties`, { properties });
}
