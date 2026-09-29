package com.operion.portal;

import java.time.LocalDate;

import com.operion.hr.LeaveRequest;

public record TeacherLeaveRequestSummary(
		Long id, String leaveTypeName, LocalDate startDate, LocalDate endDate, double numberOfDays, String status, String reason) {

	public static TeacherLeaveRequestSummary from(LeaveRequest leaveRequest) {
		return new TeacherLeaveRequestSummary(leaveRequest.getId(), leaveRequest.getLeaveType().getName(), leaveRequest.getStartDate(),
				leaveRequest.getEndDate(), leaveRequest.getNumberOfDays(), leaveRequest.getStatus().name(), leaveRequest.getReason());
	}
}
