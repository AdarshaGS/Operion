package com.operion.examination.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record UpdateExaminationSettingsRequest(boolean rankingEnabled, @NotBlank String passFailStrategy,
		@DecimalMin(value = "0", message = "must be a percentage between 0 and 100") @DecimalMax(value = "100", message = "must be a percentage between 0 and 100") double minimumAggregatePercentage) {
}
