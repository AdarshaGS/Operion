package com.operion.platform.crm.api;

import com.operion.platform.crm.Contact;

public record ContactResponse(Long id, Long leadId, Long organisationId, String name, String role, String email, String phone) {

	public static ContactResponse from(Contact contact) {
		return new ContactResponse(contact.getId(), contact.getLeadId(), contact.getOrganisationId(), contact.getName(),
				contact.getRole(), contact.getEmail(), contact.getPhone());
	}
}
