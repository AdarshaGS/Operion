package com.operion.student.api;

import java.time.LocalDate;

import com.operion.student.Student;

public record StudentResponse(Long id, Long personId, String admissionNumber, LocalDate admissionDate,
		String admissionSource, String previousSchool, String tcNumber, Double entranceScore, String bloodGroup,
		String category, String nationality, String remarks, String status) {

	static StudentResponse from(Student student) {
		return new StudentResponse(student.getId(), student.getPerson().getId(), student.getAdmissionNumber(),
				student.getAdmissionDate(), student.getAdmissionSource(), student.getPreviousSchool(),
				student.getTcNumber(), student.getEntranceScore(), student.getBloodGroup(), student.getCategory(),
				student.getNationality(), student.getRemarks(), student.getStatus().name());
	}
}
