package com.operion.academic.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSchoolClassRequest(@NotNull Long academicYearId, @NotNull Long campusId, @NotNull Long gradeLevelId, @NotBlank String displayName) {
}
