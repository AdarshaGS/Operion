package com.operion.organisation.api;

import jakarta.validation.constraints.Email;

/** Partial update: a field left out (null) keeps its stored value, an empty string clears an optional one. */
public record UpdateOrganisationProfileRequest(String name, String legalName, String logoUrl, String primaryContactName,
		@Email String primaryContactEmail, String primaryContactPhone, String addressLine1, String addressLine2, String city,
		String state, String country, String pincode, String taxIdentifier) {
}
