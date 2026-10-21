package com.operion.authorization;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Answers "is this caller a self-service portal user only?" for endpoints that are
 * deliberately left without {@code @RequirePermission} (cross-cutting lookups) but must not
 * hand an org-wide directory to a guardian. Same rule as PortalIdentityService.resolve():
 * an Owner or any ACTIVE non-portal role makes the session an admin session.
 */
@Component
public class PortalSessionPolicy {

	private final OrganisationMembershipRepository membershipRepository;

	public PortalSessionPolicy(OrganisationMembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	public boolean isPortalOnly(Long userId) {
		if (userId == null) {
			return false;
		}
		List<OrganisationMembership> active = membershipRepository.findByUserId(userId).stream()
				.filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
				.toList();
		return !active.isEmpty()
				&& active.stream().noneMatch(membership -> membership.isOwner() || !membership.getRole().isPortal());
	}
}
