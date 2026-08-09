package com.operion.attendance.api;

import java.time.LocalDate;
import java.util.List;

public record MarkAttendanceRequest(LocalDate attendanceDate, List<StudentMarkEntry> marks) {
}
