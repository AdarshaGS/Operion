package com.operion.attendance.api;

import java.util.List;

import com.operion.attendance.ClassAttendanceRegister;
import com.operion.attendance.StudentAttendance;

public record AttendanceRegisterResponse(ClassAttendanceRegisterResponse register, List<StudentAttendanceResponse> entries) {

	static AttendanceRegisterResponse of(ClassAttendanceRegister register, List<StudentAttendance> entries) {
		return new AttendanceRegisterResponse(
				ClassAttendanceRegisterResponse.from(register), entries.stream().map(StudentAttendanceResponse::from).toList());
	}
}
