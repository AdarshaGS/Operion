package com.operion.payroll.api;

import com.operion.payroll.CalculationBasis;
import com.operion.payroll.ComponentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSalaryComponentRequest(@NotBlank @Size(max = 100) String name, @NotNull ComponentType type,
		@NotNull CalculationBasis calculationBasis) {
}
