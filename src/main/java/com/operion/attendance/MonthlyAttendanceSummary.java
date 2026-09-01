package com.operion.attendance;

/**
 * Aggregated PRESENT/ABSENT/LATE/HALF_DAY/LEAVE counts for one enrollment over one
 * calendar month, plus a derived percentage. LEAVE days are excluded from the
 * percentage's denominator (a pre-approved leave isn't a working day the student was
 * expected to attend), HALF_DAY counts as half a present day in the numerator, and
 * excused/unexcused ABSENT are treated the same - both reduce the percentage, since
 * "excused" only matters for disciplinary follow-up, not for the attendance rate
 * itself. Per issue #118.
 */
public record MonthlyAttendanceSummary(
		int totalMarkedDays, int presentCount, int absentCount, int lateCount, int halfDayCount, int leaveCount,
		double percentage) {
}
