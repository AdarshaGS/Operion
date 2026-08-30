package com.operion.student.api;

import java.time.Instant;
import java.time.LocalDate;

import com.operion.student.StudentApplication;

public record StudentApplicationResponse(Long id, String applicantName, LocalDate dateOfBirth, String gender,
		String guardianName, String guardianPhone, Long desiredGradeLevelId, String notes, String status,
		Instant appliedAt, Long decidedBy, Instant decidedAt) {

	static StudentApplicationResponse from(StudentApplication application) {
		return new StudentApplicationResponse(application.getId(), application.getApplicantName(), application.getDateOfBirth(),
				application.getGender(), application.getGuardianName(), application.getGuardianPhone(),
				application.getDesiredGradeLevelId(), application.getNotes(), application.getStatus().name(),
				application.getAppliedAt(), application.getDecidedBy(), application.getDecidedAt());
	}
}
