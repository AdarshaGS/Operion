package com.operion.attendance.api;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.attendance.StudentAttendance;

public record StudentAttendanceResponse(Long id, Long studentEnrollmentId, Long academicYearId, Long schoolClassId,
		Long sectionId, LocalDate attendanceDate, String attendanceStatus, boolean excused, String remarks,
		Long markedBy, Instant markedAt, Long correctedBy, Instant correctedAt) {

	static StudentAttendanceResponse from(StudentAttendance attendance) {
		boolean wasCorrected = !attendance.getUpdatedAt().equals(attendance.getCreatedAt());
		return new StudentAttendanceResponse(attendance.getId(), attendance.getStudentEnrollment().getId(),
				attendance.getAcademicYear().getId(), attendance.getSchoolClass().getId(), attendance.getSection().getId(),
				attendance.getAttendanceDate(), attendance.getAttendanceStatus().name(), attendance.isExcused(),
				attendance.getRemarks(), attendance.getCreatedBy(), attendance.getCreatedAt(),
				wasCorrected ? attendance.getUpdatedBy() : null, wasCorrected ? attendance.getUpdatedAt() : null);
	}
}
