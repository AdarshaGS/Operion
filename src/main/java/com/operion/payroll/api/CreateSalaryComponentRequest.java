package com.operion.payroll.api;

import com.operion.payroll.CalculationBasis;
import com.operion.payroll.ComponentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSalaryComponentRequest(@NotBlank String name, @NotNull ComponentType type,
		@NotNull CalculationBasis calculationBasis) {
}
