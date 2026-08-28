package com.operion.organisation.api;

public record UpdateOrganisationProfileRequest(String name, String legalName, String logoUrl, String primaryContactName,
		String primaryContactEmail, String primaryContactPhone, String addressLine1, String addressLine2, String city,
		String state, String country, String pincode, String taxIdentifier) {
}
