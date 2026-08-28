package com.operion.organisation.api;

import com.operion.organisation.Organisation;

public record OrganisationResponse(Long id, String name, String legalName, String slug, String status,
		String organisationType, String board, String schoolCode) {

	static OrganisationResponse from(Organisation organisation) {
		return new OrganisationResponse(organisation.getId(), organisation.getName(), organisation.getLegalName(),
				organisation.getSlug(), organisation.getStatus().name(), organisation.getOrganisationType().name(),
				organisation.getBoard() != null ? organisation.getBoard().name() : null, organisation.getSchoolCode());
	}
}
