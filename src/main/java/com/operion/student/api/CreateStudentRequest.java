package com.operion.student.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStudentRequest(@NotNull Long personId, @NotBlank String admissionNumber,
		@NotNull LocalDate admissionDate, String admissionSource, String previousSchool, String tcNumber,
		Double entranceScore, String bloodGroup, String category, String nationality, String remarks) {
}
