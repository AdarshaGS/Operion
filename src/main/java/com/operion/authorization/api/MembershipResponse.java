package com.operion.authorization.api;

import com.operion.authorization.OrganisationMembership;

public record MembershipResponse(Long id, Long userId, Long personId, String personName, Long roleId, String roleName,
		Long campusId, Long departmentId, String departmentName, String status) {

	public static MembershipResponse from(OrganisationMembership membership) {
		return new MembershipResponse(membership.getId(), membership.getUser().getId(), membership.getPerson().getId(),
				membership.getPerson().getFirstName() + " " + membership.getPerson().getLastName(),
				membership.getRole().getId(), membership.getRole().getName(),
				membership.getCampus() == null ? null : membership.getCampus().getId(),
				membership.getDepartment() == null ? null : membership.getDepartment().getId(),
				membership.getDepartment() == null ? null : membership.getDepartment().getName(),
				membership.getStatus().name());
	}
}
