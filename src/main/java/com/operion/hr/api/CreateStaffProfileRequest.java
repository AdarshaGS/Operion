package com.operion.hr.api;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** reportingManagerId is nullable - who this staff member reports to. {@code access} is optional: when present the
 * staff member is also given a login (see StaffAccessService); when absent only the staff profile is created.
 * {@code employeeCode} is capped at the staff_profiles.employee_code column length (30). */
public record CreateStaffProfileRequest(Long personId, Long campusId, @Size(max = 30) String employeeCode, Long designationId,
		Long departmentId, LocalDate dateOfJoining, String employmentType, Long reportingManagerId, @Valid StaffAccessRequest access) {
}
