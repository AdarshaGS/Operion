import { api } from "./client";

export interface OrganisationBrandingResponse {
	logoRef: string | null;
	logoUrl: string | null;
	stampRef: string | null;
	stampUrl: string | null;
	signatureRef: string | null;
	signatureUrl: string | null;
	schoolNameOverride: string | null;
	addressLine: string | null;
	affiliationText: string | null;
}

export interface UpdateOrganisationBrandingRequest {
	logoRef: string | null;
	stampRef: string | null;
	signatureRef: string | null;
	schoolNameOverride: string | null;
	addressLine: string | null;
	affiliationText: string | null;
}

export function getOrganisationBranding(): Promise<OrganisationBrandingResponse> {
	return api.get<OrganisationBrandingResponse>("/api/v1/organisation/branding");
}

export function updateOrganisationBranding(request: UpdateOrganisationBrandingRequest): Promise<OrganisationBrandingResponse> {
	return api.put<OrganisationBrandingResponse>("/api/v1/organisation/branding", request);
}
