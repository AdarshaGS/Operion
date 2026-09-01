import { platformApi } from "./platformClient";

export interface OrganisationExternalServiceResponse {
	serviceKey: string;
	displayName: string;
	enabled: boolean;
}

export function listOrganisationExternalServices(organisationId: number): Promise<OrganisationExternalServiceResponse[]> {
	return platformApi.get<OrganisationExternalServiceResponse[]>(`/api/v1/platform/organisations/${organisationId}/external-services`);
}

export function setOrganisationExternalServiceEnabled(
	organisationId: number,
	serviceKey: string,
	enabled: boolean,
): Promise<OrganisationExternalServiceResponse> {
	return platformApi.patch<OrganisationExternalServiceResponse>(
		`/api/v1/platform/organisations/${organisationId}/external-services/${serviceKey}`,
		{ enabled },
	);
}
