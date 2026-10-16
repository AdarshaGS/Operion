package com.operion.attendance.api;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckInStaffRequest(
		@NotNull Long personId, @NotNull Long campusId, @NotNull LocalDate attendanceDate, @NotBlank String status,
		Instant checkInTime, String remarks) {
}
