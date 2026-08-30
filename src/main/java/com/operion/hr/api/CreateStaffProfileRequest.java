package com.operion.hr.api;

import java.time.LocalDate;

/** reportingManagerId is nullable - who this staff member reports to. */
public record CreateStaffProfileRequest(Long personId, Long campusId, String employeeCode, Long designationId,
		Long departmentId, LocalDate dateOfJoining, String employmentType, Long reportingManagerId) {
}
