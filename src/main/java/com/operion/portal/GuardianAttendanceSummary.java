package com.operion.portal;

import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.MonthlyAttendanceSummary;

public record GuardianAttendanceSummary(AttendanceStatus todayStatus, MonthlyAttendanceSummary monthly) {
}
