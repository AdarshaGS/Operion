package com.operion.hr.api;

import java.time.LocalDate;

import com.operion.hr.StaffProfile;
import com.operion.identity.api.StaffInviteResponse;

/** {@code access} is whether this person can sign in - null (omitted) for callers who hold neither MEMBERSHIP_VIEW nor
 * MEMBERSHIP_MANAGE; {@code invite} is set only on the responses that just issued a
 * login (create-with-access, give-access) - it carries the one-time claim token, so it is never returned by reads. */
public record StaffProfileResponse(Long id, Long personId, String address, Long campusId, String employeeCode, Long designationId,
		String designationName, Long departmentId, String departmentName, Long reportingManagerId, LocalDate dateOfJoining,
		String employmentType, String status, StaffAccessSummary access, StaffInviteResponse invite) {

	public static StaffProfileResponse from(StaffProfile staffProfile, StaffAccessSummary access) {
		return from(staffProfile, access, null);
	}

	public static StaffProfileResponse from(StaffProfile staffProfile, StaffAccessSummary access, StaffInviteResponse invite) {
		return new StaffProfileResponse(staffProfile.getId(), staffProfile.getPerson().getId(), staffProfile.getPerson().getAddress(),
				staffProfile.getCampus() == null ? null : staffProfile.getCampus().getId(), staffProfile.getEmployeeCode(),
				staffProfile.getDesignation().getId(), staffProfile.getDesignation().getName(),
				staffProfile.getDepartment() == null ? null : staffProfile.getDepartment().getId(),
				staffProfile.getDepartment() == null ? null : staffProfile.getDepartment().getName(),
				staffProfile.getReportingManager() == null ? null : staffProfile.getReportingManager().getId(),
				staffProfile.getDateOfJoining(), staffProfile.getEmploymentType().name(), staffProfile.getStatus().name(),
				access, invite);
	}
}
