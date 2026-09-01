package com.operion.attendance.api;

import com.operion.attendance.MonthlyAttendanceSummary;

public record MonthlyAttendanceSummaryResponse(
		int totalMarkedDays, int presentCount, int absentCount, int lateCount, int halfDayCount, int leaveCount,
		double percentage) {

	static MonthlyAttendanceSummaryResponse from(MonthlyAttendanceSummary summary) {
		return new MonthlyAttendanceSummaryResponse(summary.totalMarkedDays(), summary.presentCount(),
				summary.absentCount(), summary.lateCount(), summary.halfDayCount(), summary.leaveCount(),
				summary.percentage());
	}
}
