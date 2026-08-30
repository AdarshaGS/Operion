package com.operion.authorization;

import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Imperative equivalent of {@link RequirePermission} for call sites that need to check a
 * permission mid-method (e.g. gating individual response fields) rather than denying the
 * whole endpoint - mirrors PermissionInterceptor's owner/ALL_FUNCTIONS bypass exactly so
 * the two never disagree about who counts as authorized.
 */
@Service
public class PermissionChecker {

	private final OrganisationMembershipRepository membershipRepository;

	public PermissionChecker(OrganisationMembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	public boolean has(Long actorId, String code) {
		if (actorId == null) {
			return false;
		}
		if (membershipRepository.existsByUserIdAndStatusAndOwner(actorId, MembershipStatus.ACTIVE, true)) {
			return true;
		}
		Set<String> grantedCodes = membershipRepository.findActivePermissionCodesForUser(actorId);
		return grantedCodes.contains("ALL_FUNCTIONS") || grantedCodes.contains(code);
	}
}
