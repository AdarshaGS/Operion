package com.operion.organisation.api;

import jakarta.validation.constraints.NotBlank;

/**
 * logoRef/stampRef/signatureRef are references returned by a prior
 * {@code POST /api/v1/assets} call - the frontend uploads first, then saves the
 * reference here. Null clears that slot rather than leaving it unspecified; the frontend
 * always sends every field's current value, matching this project's other
 * "PATCH replaces every field" endpoints (see OrganisationProfileController).
 *
 * <p>The three number-format fields are {@code @NotBlank} - unlike the free-text fields
 * above, a blank format would break every future admission/invoice/receipt number, so
 * they can be edited but never cleared (see OrganisationBranding's constructor defaults).
 */
public record UpdateOrganisationBrandingRequest(String logoRef, String stampRef, String signatureRef, String schoolNameOverride,
		String addressLine, String affiliationText, String footerText, @NotBlank String admissionNumberFormat,
		@NotBlank String invoiceNumberFormat, @NotBlank String receiptNumberFormat) {
}
