package com.operion.performance.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCycleRequest(@NotBlank String name, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
}
