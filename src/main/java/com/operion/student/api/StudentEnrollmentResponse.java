package com.operion.student.api;

import java.time.LocalDate;

import com.operion.student.StudentEnrollment;

public record StudentEnrollmentResponse(Long id, Long studentId, Long academicYearId, Long sectionId,
		Integer rollNumber, String enrollmentStatus, boolean current, LocalDate enrolledDate, LocalDate exitDate) {

	static StudentEnrollmentResponse from(StudentEnrollment enrollment) {
		return new StudentEnrollmentResponse(enrollment.getId(), enrollment.getStudent().getId(),
				enrollment.getAcademicYear().getId(), enrollment.getSection().getId(), enrollment.getRollNumber(),
				enrollment.getEnrollmentStatus().name(), enrollment.isCurrent(), enrollment.getEnrolledDate(),
				enrollment.getExitDate());
	}
}
