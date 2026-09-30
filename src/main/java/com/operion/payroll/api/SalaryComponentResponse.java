package com.operion.payroll.api;

import com.operion.payroll.CalculationBasis;
import com.operion.payroll.ComponentType;
import com.operion.payroll.SalaryComponent;
import com.operion.payroll.SalaryComponentStatus;

public record SalaryComponentResponse(Long id, String name, ComponentType type, CalculationBasis calculationBasis,
		SalaryComponentStatus status) {

	static SalaryComponentResponse from(SalaryComponent component) {
		return new SalaryComponentResponse(component.getId(), component.getName(), component.getType(),
				component.getCalculationBasis(), component.getStatus());
	}
}
