package com.operion.student.api;

import jakarta.validation.constraints.NotBlank;


public record VerifyDocumentRequest(@NotBlank String verificationStatus, Long verifiedBy) {
}
