package com.operion.portal;

public record TeacherLeaveBalanceSummary(Long leaveTypeId, String leaveTypeName, double allocatedDays, double remainingDays) {
}
