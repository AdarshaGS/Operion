package com.operion.platform.crm.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateLeadRequest(@NotBlank String schoolName, @NotNull String source, Integer estimatedStudentCount,
		Long ownerId, String notes) {
}
