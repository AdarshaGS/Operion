package com.operion.attendance.api;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.attendance.StaffAttendance;

public record StaffAttendanceResponse(Long id, Long personId, Long campusId, LocalDate attendanceDate,
		String attendanceStatus, Instant checkInTime, Instant checkOutTime, String remarks) {

	static StaffAttendanceResponse from(StaffAttendance attendance) {
		return new StaffAttendanceResponse(attendance.getId(), attendance.getPerson().getId(), attendance.getCampus().getId(),
				attendance.getAttendanceDate(), attendance.getAttendanceStatus().name(), attendance.getCheckInTime(),
				attendance.getCheckOutTime(), attendance.getRemarks());
	}
}
