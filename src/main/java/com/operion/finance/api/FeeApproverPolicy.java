package com.operion.finance.api;

import java.util.List;

import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import org.springframework.stereotype.Component;

/**
 * Single definition of "who may sign off a fee waiver/refund/adjustment/discount" - an ACTIVE
 * member of the current org who is an Owner or holds FEE_STRUCTURE_MANAGE. Backs both the
 * approver picker (FeeApproverController) and server-side validation of the {@code approvedBy}
 * user id the write endpoints receive, so a hand-crafted request can't name someone outside
 * that set (#280). Lives with the controllers, not FeeService, so the many hand-built
 * FeeService instances in tests don't need a membership repository.
 */
@Component
public class FeeApproverPolicy {

	static final String APPROVER_PERMISSION = "FEE_STRUCTURE_MANAGE";

	private final OrganisationMembershipRepository membershipRepository;

	public FeeApproverPolicy(OrganisationMembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	public List<OrganisationMembership> eligibleMemberships() {
		return membershipRepository.findActiveMembersWithPermission(APPROVER_PERMISSION);
	}

	/** No-op for null - callers that require an approver already enforce that themselves. */
	public void requireEligible(Long approvedBy) {
		if (approvedBy == null) {
			return;
		}
		boolean eligible = eligibleMemberships().stream().anyMatch(m -> m.getUser().getId().equals(approvedBy));
		if (!eligible) {
			throw new IllegalArgumentException("User " + approvedBy + " is not an eligible fee approver in this organisation");
		}
	}
}
