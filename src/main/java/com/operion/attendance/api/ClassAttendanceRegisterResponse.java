package com.operion.attendance.api;

import java.time.LocalDate;

import com.operion.attendance.ClassAttendanceRegister;

public record ClassAttendanceRegisterResponse(
		Long id, Long academicYearId, Long sectionId, LocalDate attendanceDate, String registerStatus) {

	static ClassAttendanceRegisterResponse from(ClassAttendanceRegister register) {
		return new ClassAttendanceRegisterResponse(register.getId(), register.getAcademicYear().getId(),
				register.getSection().getId(), register.getAttendanceDate(), register.getRegisterStatus().name());
	}
}
