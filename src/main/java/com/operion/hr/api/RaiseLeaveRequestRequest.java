package com.operion.hr.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

/** The reason length matches leave_requests.reason (500). */
public record RaiseLeaveRequestRequest(Long staffProfileId, Long leaveTypeId, Long academicYearId, LocalDate startDate,
		LocalDate endDate, double numberOfDays, @Size(max = 500) String reason) {
}
