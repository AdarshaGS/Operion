package com.operion.hr.api;

public record AllocateLeaveBalanceRequest(Long staffProfileId, Long leaveTypeId, Long academicYearId, double allocatedDays) {
}
