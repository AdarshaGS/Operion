package com.operion.hr.api;

import java.time.LocalDate;

import com.operion.hr.StaffAssignment;

public record StaffAssignmentResponse(Long id, Long staffProfileId, Long campusId, Long departmentId, Long designationId,
		String designationName, LocalDate startDate, LocalDate endDate, String status) {

	public static StaffAssignmentResponse from(StaffAssignment assignment) {
		return new StaffAssignmentResponse(assignment.getId(), assignment.getStaffProfile().getId(),
				assignment.getCampus() == null ? null : assignment.getCampus().getId(),
				assignment.getDepartment() == null ? null : assignment.getDepartment().getId(),
				assignment.getDesignation().getId(), assignment.getDesignation().getName(),
				assignment.getStartDate(), assignment.getEndDate(), assignment.getStatus().name());
	}
}
