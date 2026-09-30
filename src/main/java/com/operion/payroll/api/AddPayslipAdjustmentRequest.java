package com.operion.payroll.api;

import java.math.BigDecimal;

import com.operion.payroll.ComponentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddPayslipAdjustmentRequest(@NotBlank String label, @NotNull ComponentType type,
		@NotNull BigDecimal amount) {
}
