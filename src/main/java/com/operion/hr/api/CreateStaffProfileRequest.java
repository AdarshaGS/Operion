package com.operion.hr.api;

import java.time.LocalDate;

public record CreateStaffProfileRequest(Long personId, Long campusId, String employeeCode, Long designationId,
		Long departmentId, LocalDate dateOfJoining, String employmentType) {
}
