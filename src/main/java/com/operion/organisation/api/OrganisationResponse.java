package com.operion.organisation.api;

import com.operion.organisation.Organisation;

public record OrganisationResponse(Long id, String name, String legalName, String slug, String status) {

	static OrganisationResponse from(Organisation organisation) {
		return new OrganisationResponse(organisation.getId(), organisation.getName(), organisation.getLegalName(),
				organisation.getSlug(), organisation.getStatus().name());
	}
}
