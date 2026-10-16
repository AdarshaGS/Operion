package com.operion.payroll;

import java.math.BigDecimal;

import com.operion.common.InvalidRequestException;
import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** value is an amount for a FLAT component, a 0-100 percentage of basic for PERCENT_OF_BASIC. */
@Getter
@Entity
@Table(name = "salary_structure_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalaryStructureLine extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "salary_structure_id")
	private SalaryStructure salaryStructure;

	@ManyToOne(optional = false)
	@JoinColumn(name = "salary_component_id")
	private SalaryComponent component;

	@Column(name = "line_value", nullable = false, precision = 14, scale = 4)
	private BigDecimal value;

	public SalaryStructureLine(SalaryStructure salaryStructure, SalaryComponent component, BigDecimal value) {
		if (value == null || value.signum() < 0) {
			throw new InvalidRequestException("Component value must be zero or more");
		}
		if (component.getCalculationBasis() == CalculationBasis.PERCENT_OF_BASIC
				&& value.compareTo(BigDecimal.valueOf(100)) > 0) {
			throw new InvalidRequestException("A percentage component cannot exceed 100");
		}
		this.salaryStructure = salaryStructure;
		this.component = component;
		this.value = value;
	}
}
