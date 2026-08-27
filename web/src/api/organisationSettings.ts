import { api } from "./client";

export interface OrganisationConfigurationResponse {
	timezone: string | null;
	defaultCurrency: string | null;
	dateFormat: string | null;
	workingDaysMask: number;
	logoUrl: string | null;
	primaryColor: string | null;
}

export interface UpdateOrganisationConfigurationRequest {
	timezone: string | null;
	defaultCurrency: string | null;
	dateFormat: string | null;
	workingDaysMask: number;
	logoUrl: string | null;
	primaryColor: string | null;
}

export function getOrganisationSettings(): Promise<OrganisationConfigurationResponse> {
	return api.get<OrganisationConfigurationResponse>("/api/v1/organisations/settings");
}

export function updateOrganisationSettings(
	request: UpdateOrganisationConfigurationRequest,
): Promise<OrganisationConfigurationResponse> {
	return api.patch<OrganisationConfigurationResponse>("/api/v1/organisations/settings", request);
}
