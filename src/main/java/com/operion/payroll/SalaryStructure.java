package com.operion.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.InvalidRequestException;
import com.operion.common.TenantScopedEntity;
import com.operion.hr.StaffProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A staff member's compensation from effectiveFrom onward. Insert-only: a raise adds a new
 * row rather than editing this one, so past payroll stays explainable. The structure in force
 * on a date is the latest row with effectiveFrom on or before it. Its components live in
 * SalaryStructureLine (FK only, no owning collection - same convention as PurchaseOrderLine).
 */
@Getter
@Entity
@Table(name = "salary_structures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalaryStructure extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "staff_profile_id")
	private StaffProfile staffProfile;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "basic_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal basicAmount;

	public SalaryStructure(StaffProfile staffProfile, LocalDate effectiveFrom, BigDecimal basicAmount) {
		if (effectiveFrom == null) {
			throw new InvalidRequestException("Effective-from date is required");
		}
		if (basicAmount == null || basicAmount.signum() < 0) {
			throw new InvalidRequestException("Basic amount must be zero or more");
		}
		this.staffProfile = staffProfile;
		this.effectiveFrom = effectiveFrom;
		this.basicAmount = basicAmount;
	}
}
