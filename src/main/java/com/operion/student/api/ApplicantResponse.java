package com.operion.student.api;

import java.time.LocalDate;

import com.operion.student.Applicant;

public record ApplicantResponse(Long id, Long personId, String firstName, String lastName, LocalDate dateOfBirth, String gender,
		LocalDate inquiryDate, String source, String notes, String status, GradeRef desiredGrade, String guardianName,
		String guardianPhone, String guardianEmail, String rejectionReason, Long studentId) {

	public record GradeRef(Long id, String name) {
	}

	static ApplicantResponse from(Applicant applicant) {
		return from(applicant, null);
	}

	/** {@code studentId} is the student a CONVERTED applicant became; null for every other status. */
	static ApplicantResponse from(Applicant applicant, Long studentId) {
		var person = applicant.getPerson();
		var grade = applicant.getDesiredGrade();
		return new ApplicantResponse(applicant.getId(), person.getId(), person.getFirstName(), person.getLastName(),
				person.getDateOfBirth(), person.getGender(), applicant.getInquiryDate(), applicant.getSource(),
				applicant.getNotes(), applicant.getStatus().name(),
				grade == null ? null : new GradeRef(grade.getId(), grade.getName()), applicant.getGuardianName(),
				applicant.getGuardianPhone(), applicant.getGuardianEmail(), applicant.getRejectionReason(), studentId);
	}
}
