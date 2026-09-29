package com.operion.student.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectApplicantRequest(@NotBlank @Size(max = 1000) String reason) {
}
