package com.operion.attendance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StudentMarkEntry(@NotNull Long studentEnrollmentId, @NotBlank String status, boolean excused, String remarks) {
}
