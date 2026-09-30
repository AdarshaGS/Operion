package com.operion.payroll.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.operion.payroll.CalculationBasis;
import com.operion.payroll.ComponentType;
import com.operion.payroll.SalaryStructure;
import com.operion.payroll.SalaryStructureLine;

public record SalaryStructureResponse(Long id, Long staffProfileId, LocalDate effectiveFrom, BigDecimal basicAmount,
		List<LineResponse> lines) {

	public record LineResponse(Long componentId, String componentName, ComponentType type,
			CalculationBasis calculationBasis, BigDecimal value) {
	}

	static SalaryStructureResponse from(SalaryStructure structure, List<SalaryStructureLine> lines) {
		return new SalaryStructureResponse(structure.getId(), structure.getStaffProfile().getId(),
				structure.getEffectiveFrom(), structure.getBasicAmount(),
				lines.stream().map(line -> new LineResponse(line.getComponent().getId(), line.getComponent().getName(),
						line.getComponent().getType(), line.getComponent().getCalculationBasis(), line.getValue()))
						.toList());
	}
}
