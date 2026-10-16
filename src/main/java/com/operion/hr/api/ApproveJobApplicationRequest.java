package com.operion.hr.api;

import java.time.LocalDate;

/**
 * Optional body for approving an application. Supplying any hire detail makes approval also
 * create the applicant's person and staff profile, which then needs designationId,
 * employmentType, dateOfJoining and employeeCode; with no body the call only records the decision.
 * campusId and departmentId stay nullable (org-wide / unassigned).
 */
public record ApproveJobApplicationRequest(Long designationId, String employmentType, LocalDate dateOfJoining,
		String employeeCode, Long campusId, Long departmentId) {

	boolean hasHireDetails() {
		return designationId != null || employmentType != null || dateOfJoining != null || employeeCode != null
				|| campusId != null || departmentId != null;
	}
}
