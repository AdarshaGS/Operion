package com.operion.hr.api;

import java.time.LocalDate;

import com.operion.hr.StaffProfile;

public record StaffProfileResponse(Long id, Long personId, String address, Long campusId, String employeeCode, Long designationId,
		String designationName, Long departmentId, String departmentName, Long reportingManagerId, LocalDate dateOfJoining,
		String employmentType, String status) {

	public static StaffProfileResponse from(StaffProfile staffProfile) {
		return new StaffProfileResponse(staffProfile.getId(), staffProfile.getPerson().getId(), staffProfile.getPerson().getAddress(),
				staffProfile.getCampus() == null ? null : staffProfile.getCampus().getId(), staffProfile.getEmployeeCode(),
				staffProfile.getDesignation().getId(), staffProfile.getDesignation().getName(),
				staffProfile.getDepartment() == null ? null : staffProfile.getDepartment().getId(),
				staffProfile.getDepartment() == null ? null : staffProfile.getDepartment().getName(),
				staffProfile.getReportingManager() == null ? null : staffProfile.getReportingManager().getId(),
				staffProfile.getDateOfJoining(), staffProfile.getEmploymentType().name(), staffProfile.getStatus().name());
	}
}
