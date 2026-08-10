package com.operion.hr.api;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.hr.LeaveRequest;

public record LeaveRequestResponse(Long id, Long staffProfileId, Long leaveTypeId, Long academicYearId, LocalDate startDate,
		LocalDate endDate, double numberOfDays, String reason, String status, Long approvedBy, Instant decidedAt) {

	public static LeaveRequestResponse from(LeaveRequest leaveRequest) {
		return new LeaveRequestResponse(leaveRequest.getId(), leaveRequest.getStaffProfile().getId(), leaveRequest.getLeaveType().getId(),
				leaveRequest.getAcademicYear().getId(), leaveRequest.getStartDate(), leaveRequest.getEndDate(),
				leaveRequest.getNumberOfDays(), leaveRequest.getReason(), leaveRequest.getStatus().name(),
				leaveRequest.getApprovedBy(), leaveRequest.getDecidedAt());
	}
}
