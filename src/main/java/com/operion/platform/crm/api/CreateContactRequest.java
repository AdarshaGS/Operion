package com.operion.platform.crm.api;

import jakarta.validation.constraints.NotBlank;

/** Exactly one of leadId / organisationId. */
public record CreateContactRequest(Long leadId, Long organisationId, @NotBlank String name, String role, String email,
		String phone) {
}
