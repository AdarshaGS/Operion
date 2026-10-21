package com.operion.authorization;

import java.util.Collection;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * The one "does this caller hold any of these codes?" rule - Owner bypass, then the
 * ALL_FUNCTIONS grant, then the granular codes. PermissionInterceptor applies it to
 * {@code @RequirePermission}; services call it directly when one request mixes actions
 * that need different permissions (e.g. a promotion batch that also graduates students).
 */
@Component
public class PermissionChecker {

	private final OrganisationMembershipRepository membershipRepository;

	public PermissionChecker(OrganisationMembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	public boolean holdsAny(Long userId, Collection<String> codes) {
		if (userId == null) {
			return false;
		}
		// The Owner's membership is the org's "*" capability - bypassing the granular check
		// entirely (rather than enumerating every catalog code onto their role) means a
		// permission added to the catalog tomorrow automatically covers every existing
		// Owner with no data backfill.
		if (membershipRepository.existsByUserIdAndStatusAndOwner(userId, MembershipStatus.ACTIVE, true)) {
			return true;
		}
		// ALL_FUNCTIONS (GitHub #200) is the assignable-to-any-role equivalent of the Owner
		// bypass above - lets an org grant a *custom* role blanket admin access via the
		// normal Role -> Permission chain, without needing to be the fixed Owner membership.
		Set<String> grantedCodes = membershipRepository.findActivePermissionCodesForUser(userId);
		return grantedCodes.contains("ALL_FUNCTIONS") || codes.stream().anyMatch(grantedCodes::contains);
	}

	/** Delegation rule: a caller can only hand out (via a role or a membership) what they hold themselves. */
	public boolean holdsAll(Long userId, Collection<String> codes) {
		if (userId == null) {
			return false;
		}
		if (codes.isEmpty() || membershipRepository.existsByUserIdAndStatusAndOwner(userId, MembershipStatus.ACTIVE, true)) {
			return true;
		}
		Set<String> grantedCodes = membershipRepository.findActivePermissionCodesForUser(userId);
		return grantedCodes.contains("ALL_FUNCTIONS") || grantedCodes.containsAll(codes);
	}

	public void requireAll(Long userId, Collection<String> codes, String message) {
		if (!holdsAll(userId, codes)) {
			throw new AuthorizationDeniedException(message);
		}
	}

	public void require(Long userId, String code) {
		if (!holdsAny(userId, Set.of(code))) {
			throw new AuthorizationDeniedException("Missing required permission: " + code);
		}
	}
}
