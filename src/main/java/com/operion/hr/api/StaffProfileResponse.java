package com.operion.hr.api;

import java.time.LocalDate;

import com.operion.hr.StaffProfile;

public record StaffProfileResponse(Long id, Long personId, Long campusId, String employeeCode, String designation,
		String department, LocalDate dateOfJoining, String employmentType, String status) {

	public static StaffProfileResponse from(StaffProfile staffProfile) {
		return new StaffProfileResponse(staffProfile.getId(), staffProfile.getPerson().getId(),
				staffProfile.getCampus() == null ? null : staffProfile.getCampus().getId(), staffProfile.getEmployeeCode(),
				staffProfile.getDesignation(), staffProfile.getDepartment(), staffProfile.getDateOfJoining(),
				staffProfile.getEmploymentType().name(), staffProfile.getStatus().name());
	}
}
