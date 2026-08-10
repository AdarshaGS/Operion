package com.operion.hr.api;

import java.time.LocalDate;

public record RaiseLeaveRequestRequest(Long staffProfileId, Long leaveTypeId, Long academicYearId, LocalDate startDate,
		LocalDate endDate, double numberOfDays, String reason) {
}
