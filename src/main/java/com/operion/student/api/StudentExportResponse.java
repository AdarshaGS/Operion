package com.operion.student.api;

import java.time.LocalDate;

import com.operion.identity.Person;
import com.operion.student.Student;

public record StudentExportResponse(Long id, String firstName, String lastName, String email, String phone,
		String admissionNumber, LocalDate admissionDate, String bloodGroup, String category, String status) {

	static StudentExportResponse from(Student student, boolean includeSensitive) {
		Person person = student.getPerson();
		return new StudentExportResponse(student.getId(), person.getFirstName(), person.getLastName(), person.getEmail(),
				person.getPhone(), student.getAdmissionNumber(), student.getAdmissionDate(), student.getBloodGroup(),
				includeSensitive ? student.getCategory() : null, student.getStatus().name());
	}
}
