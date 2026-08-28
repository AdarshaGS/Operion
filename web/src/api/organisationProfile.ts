import { api } from "./client";

export interface OrganisationProfileResponse {
	name: string;
	legalName: string | null;
	logoUrl: string | null;
	primaryContactName: string | null;
	primaryContactEmail: string | null;
	primaryContactPhone: string | null;
	addressLine1: string | null;
	addressLine2: string | null;
	city: string | null;
	state: string | null;
	pincode: string | null;
	taxIdentifier: string | null;
}

export type UpdateOrganisationProfileRequest = OrganisationProfileResponse;

export function getOrganisationProfile(): Promise<OrganisationProfileResponse> {
	return api.get<OrganisationProfileResponse>("/api/v1/organisations/profile");
}

export function updateOrganisationProfile(request: UpdateOrganisationProfileRequest): Promise<OrganisationProfileResponse> {
	return api.patch<OrganisationProfileResponse>("/api/v1/organisations/profile", request);
}
