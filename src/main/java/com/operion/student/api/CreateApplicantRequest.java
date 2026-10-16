package com.operion.student.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateApplicantRequest(@NotNull Long personId, @NotNull LocalDate inquiryDate, @Size(max = 100) String source, @Size(max = 2000) String notes) {
}
