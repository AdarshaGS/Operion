package com.operion.student.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApplicantDocumentEntry(@NotBlank String docType, @NotNull Boolean received) {
}
