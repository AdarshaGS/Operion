import { platformApi } from "./platformClient";

export interface OrganisationResponse {
	id: number;
	name: string;
	legalName: string;
	slug: string;
	status: string;
}

export interface CreateOrganisationRequest {
	name: string;
	legalName: string;
	slug: string;
	adminEmail: string;
	adminPassword: string;
	adminFirstName: string;
	adminLastName: string;
}

export function listOrganisations(): Promise<OrganisationResponse[]> {
	return platformApi.get<OrganisationResponse[]>("/api/v1/platform/organisations");
}

export function getOrganisation(id: number): Promise<OrganisationResponse> {
	return platformApi.get<OrganisationResponse>(`/api/v1/platform/organisations/${id}`);
}

export function createOrganisation(request: CreateOrganisationRequest): Promise<OrganisationResponse> {
	return platformApi.post<OrganisationResponse>("/api/v1/platform/organisations", request);
}

export function changeOrganisationStatus(id: number, status: string): Promise<OrganisationResponse> {
	return platformApi.patch<OrganisationResponse>(`/api/v1/platform/organisations/${id}/status`, { status });
}
