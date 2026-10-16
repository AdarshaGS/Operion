package com.operion.examination.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateExamRequest(@NotNull Long academicYearId, @NotBlank String name, @NotBlank String examType) {
}
