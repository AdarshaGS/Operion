package com.operion.organisation.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAcademicYearRequest(@NotBlank String name, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
}
