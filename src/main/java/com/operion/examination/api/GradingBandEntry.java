package com.operion.examination.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GradingBandEntry(@NotBlank String grade,
		@NotNull @DecimalMin(value = "0", message = "percentage must be between 0 and 100") @DecimalMax(value = "100", message = "percentage must be between 0 and 100") Double minPercentage,
		String remark) {
}
