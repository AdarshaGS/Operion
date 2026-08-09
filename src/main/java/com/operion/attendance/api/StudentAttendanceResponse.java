package com.operion.attendance.api;

import java.time.LocalDate;

import com.operion.attendance.StudentAttendance;

public record StudentAttendanceResponse(Long id, Long studentEnrollmentId, Long academicYearId, Long schoolClassId,
		Long sectionId, LocalDate attendanceDate, String attendanceStatus, boolean excused, String remarks) {

	static StudentAttendanceResponse from(StudentAttendance attendance) {
		return new StudentAttendanceResponse(attendance.getId(), attendance.getStudentEnrollment().getId(),
				attendance.getAcademicYear().getId(), attendance.getSchoolClass().getId(), attendance.getSection().getId(),
				attendance.getAttendanceDate(), attendance.getAttendanceStatus().name(), attendance.isExcused(),
				attendance.getRemarks());
	}
}
