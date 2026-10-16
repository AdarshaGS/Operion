package com.operion.academic.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateSchoolClassRequest(@NotBlank String displayName) {
}
