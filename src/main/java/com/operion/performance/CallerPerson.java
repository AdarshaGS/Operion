package com.operion.performance;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.common.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The person behind the authenticated user, through their active membership. */
@Component
public class CallerPerson {

	private final OrganisationMembershipRepository membershipRepository;

	public CallerPerson(OrganisationMembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	@Transactional(readOnly = true)
	public Long personId() {
		Long userId = TenantContext.getActorId();
		if (userId == null) {
			throw new AuthorizationDeniedException("No authenticated caller");
		}
		return membershipRepository.findByUserId(userId).stream().filter(m -> m.getStatus() == MembershipStatus.ACTIVE).findFirst()
				.orElseThrow(() -> new AuthorizationDeniedException("No active membership for this user")).getPerson().getId();
	}
}
