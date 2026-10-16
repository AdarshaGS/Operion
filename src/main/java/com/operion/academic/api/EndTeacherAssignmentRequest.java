package com.operion.academic.api;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EndTeacherAssignmentRequest(@NotNull LocalDate endDate) {
}
