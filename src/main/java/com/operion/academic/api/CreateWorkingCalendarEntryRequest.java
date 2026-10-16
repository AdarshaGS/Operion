package com.operion.academic.api;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** campusId null = applies to every campus. type: "HOLIDAY" or "SPECIAL_WORKING_DAY". */
/** campusId null = applies to every campus. type: "HOLIDAY" or "SPECIAL_WORKING_DAY". */
public record CreateWorkingCalendarEntryRequest(@NotNull Long academicYearId, Long campusId, @NotNull LocalDate date, @NotBlank String label, @NotBlank String type) {
}
