package com.operion.payroll.api;

import java.math.BigDecimal;

import com.operion.payroll.ComponentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddPayslipAdjustmentRequest(@NotBlank @Size(max = 200) String label, @NotNull ComponentType type,
		@NotNull @DecimalMax("999999999999.99") BigDecimal amount) {
}
