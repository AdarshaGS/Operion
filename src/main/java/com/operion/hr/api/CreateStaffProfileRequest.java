package com.operion.hr.api;

import java.time.LocalDate;

public record CreateStaffProfileRequest(Long personId, Long campusId, String employeeCode, String designation,
		String department, LocalDate dateOfJoining, String employmentType) {
}
