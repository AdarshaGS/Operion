package com.operion.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;

public record CreateSalaryStructureRequest(@NotNull LocalDate effectiveFrom,
		@NotNull @DecimalMax("999999999999.99") BigDecimal basicAmount, @Valid List<Line> lines) {

	public record Line(@NotNull Long componentId, @NotNull @DecimalMax("9999999999.9999") BigDecimal value) {
	}
}
