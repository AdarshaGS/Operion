package com.operion.finance.api;

import java.math.BigDecimal;

import com.operion.finance.StudentFeeAssignment;

public record OpeningBalanceExportResponse(long id, String studentAdmissionNumber, String studentName, BigDecimal amount, String status) {

	public static OpeningBalanceExportResponse from(StudentFeeAssignment assignment) {
		var student = assignment.getStudentEnrollment().getStudent();
		var person = student.getPerson();
		String name = person.getLastName() == null ? person.getFirstName() : person.getFirstName() + " " + person.getLastName();
		return new OpeningBalanceExportResponse(assignment.getId(), student.getAdmissionNumber(), name, assignment.getBaseAmount(),
				assignment.getStatus().name());
	}
}
