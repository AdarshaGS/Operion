package com.operion.organisation.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.operion.organisation.Organisation;

public record OrganisationResponse(Long id, String name, String legalName, String slug, String status,
		String organisationType, String board, String schoolCode, Instant createdAt, Instant trialEndsAt) {

	/** trialEndsAt is always computed from createdAt + trialDays, regardless of the org's
	 * current status - meaningful only while status is TRIAL, same "cheap to compute,
	 * caller decides when it's relevant" call as everything else on this DTO. Doesn't
	 * account for an org leaving and re-entering TRIAL (OrganisationStatus has no state
	 * machine, see its own javadoc) - a re-entered trial still counts from the original
	 * createdAt, a known simplification. */
	static OrganisationResponse from(Organisation organisation, int trialDays) {
		return new OrganisationResponse(organisation.getId(), organisation.getName(), organisation.getLegalName(),
				organisation.getSlug(), organisation.getStatus().name(), organisation.getOrganisationType().name(),
				organisation.getBoard() != null ? organisation.getBoard().name() : null, organisation.getSchoolCode(),
				organisation.getCreatedAt(), organisation.getCreatedAt().plus(trialDays, ChronoUnit.DAYS));
	}
}
