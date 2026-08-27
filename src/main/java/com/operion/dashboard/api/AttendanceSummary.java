package com.operion.dashboard.api;

public record AttendanceSummary(long present, long absent, long late, long halfDay, long marked, int attendanceRatePercent) {
}
