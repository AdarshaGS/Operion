package com.operion.payroll;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One org-defined earning or deduction (e.g. "Housing allowance", "Provident fund"). The
 * catalog ships empty - no country's statutory scheme is pre-named - so an org (or a future
 * region template) populates its own. type and calculation basis are immutable once created:
 * structure lines and payslips are interpreted through them, so changing either would
 * silently reinterpret existing compensation. Retire a component via status instead.
 */
@Getter
@Entity
@Table(name = "salary_components")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalaryComponent extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "component_type", nullable = false, length = 20)
	private ComponentType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "calculation_basis", nullable = false, length = 30)
	private CalculationBasis calculationBasis;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SalaryComponentStatus status;

	public SalaryComponent(String name, ComponentType type, CalculationBasis calculationBasis) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Component name is required");
		}
		this.name = name.trim();
		this.type = type;
		this.calculationBasis = calculationBasis;
		this.status = SalaryComponentStatus.ACTIVE;
	}

	public void changeStatus(SalaryComponentStatus status) {
		this.status = status;
	}
}
