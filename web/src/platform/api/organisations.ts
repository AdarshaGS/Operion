import { platformApi } from "./platformClient";

export interface OrganisationResponse {
	id: number;
	name: string;
	legalName: string;
	slug: string;
	status: string;
	organisationType: string;
	board: string | null;
	schoolCode: string | null;
}

export interface CreateOrganisationRequest {
	name: string;
	legalName: string;
	slug: string;
	adminEmail: string;
	adminPassword: string;
	adminFirstName: string;
	adminLastName: string;
	organisationType?: string | null;
	board?: string | null;
	schoolCode?: string | null;
	primaryContactName?: string | null;
	primaryContactEmail?: string | null;
	primaryContactPhone?: string | null;
	addressLine1?: string | null;
	addressLine2?: string | null;
	city?: string | null;
	state?: string | null;
	country?: string | null;
	pincode?: string | null;
	timezone?: string | null;
	academicYearName?: string | null;
	academicYearStartDate?: string | null;
	academicYearEndDate?: string | null;
	planId?: number | null;
	planStartDate?: string | null;
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
