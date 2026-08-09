package com.operion.organisation.api;

public record CreateOrganisationRequest(String name, String legalName, String slug,
		String adminEmail, String adminPassword, String adminFirstName, String adminLastName) {
}
