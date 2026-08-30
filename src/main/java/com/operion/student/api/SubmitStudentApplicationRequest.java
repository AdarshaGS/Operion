package com.operion.student.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

public record SubmitStudentApplicationRequest(@NotBlank String applicantName, LocalDate dateOfBirth, String gender,
		String guardianName, String guardianPhone, Long desiredGradeLevelId, String notes) {
}
