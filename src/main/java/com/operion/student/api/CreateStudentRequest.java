package com.operion.student.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** {@code admissionNumber} is optional - left null/blank, StudentService auto-generates
 * one from the organisation's configured numbering format (#142). */
public record CreateStudentRequest(@NotNull Long personId, String admissionNumber,
		@NotNull LocalDate admissionDate, String admissionSource, String previousSchool, String tcNumber,
		Double entranceScore, String bloodGroup, String category, String nationality, String remarks) {
}
