package com.operion.hr.api;

import com.operion.hr.LeaveType;

public record LeaveTypeResponse(Long id, String code, String name, Double defaultAnnualDays, String status) {

	public static LeaveTypeResponse from(LeaveType leaveType) {
		return new LeaveTypeResponse(leaveType.getId(), leaveType.getCode(), leaveType.getName(),
				leaveType.getDefaultAnnualDays(), leaveType.getStatus().name());
	}
}
