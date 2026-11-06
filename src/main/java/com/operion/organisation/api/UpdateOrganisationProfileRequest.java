package com.operion.organisation.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

/** Partial update: a field left out (null) keeps its stored value, an empty string clears an optional one. */
@Schema(description = "Partial update: a field left out (null) keeps its stored value, an empty string clears an optional one. "
		+ "The setup wizard needs primaryContactName and primaryContactEmail before it lets you continue.")
public record UpdateOrganisationProfileRequest(
		@Schema(description = "Display name; cannot be blank when sent", example = "Adarsh School") String name,
		@Schema(description = "Registered legal name", example = "Adarsh Education Trust") String legalName,
		@Schema(description = "Public URL of the logo image", example = "https://example.org/logo.png") String logoUrl,
		@Schema(description = "Required by the setup wizard", example = "Arun Kumar") String primaryContactName,
		@Schema(description = "Required by the setup wizard; must be a valid email address", example = "arun@example.org")
		@Email String primaryContactEmail,
		@Schema(example = "9876543210") String primaryContactPhone,
		@Schema(example = "12 MG Road") String addressLine1,
		@Schema(example = "Near City Park") String addressLine2,
		@Schema(example = "Bengaluru") String city,
		@Schema(example = "Karnataka") String state,
		@Schema(example = "India") String country,
		@Schema(example = "560001") String pincode,
		@Schema(description = "GSTIN, PAN or equivalent", example = "29ABCDE1234F1Z5") String taxIdentifier) {
}
