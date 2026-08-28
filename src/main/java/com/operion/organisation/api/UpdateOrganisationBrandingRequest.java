package com.operion.organisation.api;

/**
 * logoRef/stampRef/signatureRef are references returned by a prior
 * {@code POST /api/v1/assets} call - the frontend uploads first, then saves the
 * reference here. Null clears that slot rather than leaving it unspecified; the frontend
 * always sends every field's current value, matching this project's other
 * "PATCH replaces every field" endpoints (see OrganisationProfileController).
 */
public record UpdateOrganisationBrandingRequest(String logoRef, String stampRef, String signatureRef, String schoolNameOverride,
		String addressLine, String affiliationText) {
}
