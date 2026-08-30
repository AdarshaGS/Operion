package com.operion.student.api;

import java.time.LocalDate;

import com.operion.student.Student;

public record StudentResponse(Long id, Long personId, String admissionNumber, LocalDate admissionDate,
		String admissionSource, String previousSchool, String tcNumber, Double entranceScore, String bloodGroup,
		String category, String medicalAlerts, String nationality, String remarks, String status) {

	/** category/medicalAlerts are omitted unless the caller holds STUDENT_SENSITIVE_VIEW
	 * (or bypasses via Owner/ALL_FUNCTIONS) - see StudentController.canViewSensitive(). */
	static StudentResponse from(Student student, boolean includeSensitive) {
		return new StudentResponse(student.getId(), student.getPerson().getId(), student.getAdmissionNumber(),
				student.getAdmissionDate(), student.getAdmissionSource(), student.getPreviousSchool(),
				student.getTcNumber(), student.getEntranceScore(), student.getBloodGroup(),
				includeSensitive ? student.getCategory() : null, includeSensitive ? student.getMedicalAlerts() : null,
				student.getNationality(), student.getRemarks(), student.getStatus().name());
	}
}
