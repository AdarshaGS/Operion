package com.operion.hr.api;

public record LeaveBalanceResponse(Long staffProfileId, Long leaveTypeId, Long academicYearId, double allocatedDays, double remainingDays) {
}
