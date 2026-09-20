package com.operion.finance.api;

import com.operion.authorization.OrganisationMembership;

public record FeeApproverResponse(Long userId, String name) {

	static FeeApproverResponse from(OrganisationMembership membership) {
		return new FeeApproverResponse(membership.getUser().getId(),
				membership.getPerson().getFirstName() + " " + membership.getPerson().getLastName());
	}
}
