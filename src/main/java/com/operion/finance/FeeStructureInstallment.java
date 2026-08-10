package com.operion.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Explicit uneven split of a FeeStructure's amount (schools commonly split 40/30/30, not
 * even thirds) - FeeService validates the installments sum to FeeStructure.amount at
 * creation. No lifecycle status of its own - a child row, not an independently managed
 * entity. Per ai-context/erp-system-plan.md §3.2.
 */
@Getter
@Entity
@Table(name = "fee_structure_installments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeStructureInstallment extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "fee_structure_id")
	private FeeStructure feeStructure;

	@Column(name = "installment_number", nullable = false)
	private int installmentNumber;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	public FeeStructureInstallment(FeeStructure feeStructure, int installmentNumber, LocalDate dueDate, BigDecimal amount) {
		this.feeStructure = feeStructure;
		this.installmentNumber = installmentNumber;
		this.dueDate = dueDate;
		this.amount = amount;
	}
}
