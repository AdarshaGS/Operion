package com.operion.academic.api;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** campusId null = applies to every campus. */
/** campusId null = applies to every campus. */
public record CreateSchoolEventRequest(Long campusId, @NotNull LocalDate date, @NotBlank String title, String description) {
}
