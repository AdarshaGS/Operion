package com.operion.attendance.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MarkAttendanceRequest(@NotNull LocalDate attendanceDate, @NotNull List<@Valid @NotNull StudentMarkEntry> marks) {
}
