package com.operion.student.api;

import jakarta.validation.constraints.NotBlank;

public record ApplicantDocumentEntry(@NotBlank String docType, boolean received) {
}
