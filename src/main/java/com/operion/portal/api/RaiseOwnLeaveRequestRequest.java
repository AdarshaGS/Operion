package com.operion.portal.api;

import java.time.LocalDate;

public record RaiseOwnLeaveRequestRequest(
		Long leaveTypeId, Long academicYearId, LocalDate startDate, LocalDate endDate, double numberOfDays, String reason) {
}
