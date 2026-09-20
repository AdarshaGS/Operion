package com.operion.student.api;

import java.time.LocalDate;

import com.operion.student.StudentEnrollment;

public record StudentEnrolmentExportResponse(Long id, String admissionNumber, String academicYear, String className,
		String sectionName, Integer rollNumber, LocalDate enrolledDate, boolean current) {

	static StudentEnrolmentExportResponse from(StudentEnrollment enrollment) {
		return new StudentEnrolmentExportResponse(enrollment.getId(), enrollment.getStudent().getAdmissionNumber(),
				enrollment.getAcademicYear().getName(), enrollment.getSection().getSchoolClass().getDisplayName(),
				enrollment.getSection().getName(), enrollment.getRollNumber(), enrollment.getEnrolledDate(),
				enrollment.isCurrent());
	}
}
