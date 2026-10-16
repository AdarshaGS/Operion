import { platformApi } from "./platformClient";

export interface OrganisationModuleResponse {
	code: string;
	label: string;
	enabled: boolean;
}

export function listOrganisationModules(organisationId: number): Promise<OrganisationModuleResponse[]> {
	return platformApi.get<OrganisationModuleResponse[]>(`/api/v1/platform/organisations/${organisationId}/modules`);
}

export function setOrganisationModuleEnabled(organisationId: number, code: string, enabled: boolean): Promise<OrganisationModuleResponse> {
	return platformApi.put<OrganisationModuleResponse>(`/api/v1/platform/organisations/${organisationId}/modules/${code}`, { enabled });
}
