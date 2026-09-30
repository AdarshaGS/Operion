package com.operion.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateSalaryStructureRequest(@NotNull LocalDate effectiveFrom, @NotNull BigDecimal basicAmount,
		@Valid List<Line> lines) {

	public record Line(@NotNull Long componentId, @NotNull BigDecimal value) {
	}
}
