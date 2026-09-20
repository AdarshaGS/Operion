package com.operion.authorization.api;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.authorization.MemberStatus;
import com.operion.authorization.OrganisationMembership;

public record MembershipResponse(Long id, Long userId, Long personId, String personName, String email, String phone, Long roleId,
		String roleName, Long campusId, Long departmentId, String departmentName, String status, String memberStatus,
		String memberId, LocalDate joiningDate, Instant lastLoginAt) {

	public static MembershipResponse from(OrganisationMembership membership) {
		return new MembershipResponse(membership.getId(), membership.getUser().getId(), membership.getPerson().getId(),
				membership.getPerson().getFirstName() + " " + membership.getPerson().getLastName(),
				membership.getUser().getEmail(), membership.getUser().getPhone(),
				membership.getRole().getId(), membership.getRole().getName(),
				membership.getCampus() == null ? null : membership.getCampus().getId(),
				membership.getDepartment() == null ? null : membership.getDepartment().getId(),
				membership.getDepartment() == null ? null : membership.getDepartment().getName(),
				membership.getStatus().name(),
				MemberStatus.of(membership.getUser().getStatus(), membership.getStatus()).name(),
				membership.getMemberId(),
				membership.getJoiningDate(),
				membership.getUser().getLastLoginAt());
	}
}
