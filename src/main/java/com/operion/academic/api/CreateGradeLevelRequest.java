package com.operion.academic.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateGradeLevelRequest(@NotBlank String name, @NotNull @PositiveOrZero Integer sequenceOrder, String stage) {
}
