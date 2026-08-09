package com.operion.attendance.api;

import java.time.Instant;
import java.time.LocalDate;

public record CheckInStaffRequest(
		Long personId, Long campusId, LocalDate attendanceDate, String status, Instant checkInTime, String remarks) {
}
