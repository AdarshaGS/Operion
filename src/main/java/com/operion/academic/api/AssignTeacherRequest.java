package com.operion.academic.api;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AssignTeacherRequest(Long subjectId, @NotNull Long teacherPersonId, @NotBlank String assignmentType, @NotNull LocalDate startDate) {
}
