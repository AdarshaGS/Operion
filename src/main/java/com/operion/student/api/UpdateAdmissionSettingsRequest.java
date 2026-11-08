package com.operion.student.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateAdmissionSettingsRequest(@NotBlank String feeClearanceMode) {
}
