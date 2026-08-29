package com.operion.student.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record CreateApplicantRequest(@NotNull Long personId, @NotNull LocalDate inquiryDate, String source, String notes) {
}
