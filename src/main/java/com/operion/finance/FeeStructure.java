package com.operion.finance;

import java.math.BigDecimal;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Amount of one FeeCategory within one FeeStructureGroup - an explicit row per category,
 * not a nullable "applies to all categories" wildcard, which would need
 * fallback/precedence resolution at read time. Per ai-context/erp-system-plan.md §3.2 and
 * issue #129 (grouping).
 */
@Getter
@Entity
@Table(name = "fee_structures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeStructure extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "fee_structure_group_id")
	private FeeStructureGroup feeStructureGroup;

	@ManyToOne(optional = false)
	@JoinColumn(name = "fee_category_id")
	private FeeCategory feeCategory;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FeeStructureStatus status;

	/** Display/reporting label only - see PaymentFrequency's own javadoc. Per #281. */
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_frequency", nullable = false, length = 20)
	private PaymentFrequency paymentFrequency;

	/** Nullable - a flat discount an admin can define for payers who clear the whole
	 * FeeStructure.amount in one go instead of per-installment. Surfaced as a pre-fillable
	 * option on StudentFeeAssignment's existing discount mechanism (StudentFeesPanel), not
	 * applied automatically - a discount still needs its own reason/approver either way. */
	@Column(name = "one_shot_discount_amount", precision = 10, scale = 2)
	private BigDecimal oneShotDiscountAmount;

	public FeeStructure(FeeStructureGroup feeStructureGroup, FeeCategory feeCategory, BigDecimal amount) {
		this(feeStructureGroup, feeCategory, amount, PaymentFrequency.MONTHLY, null);
	}

	public FeeStructure(FeeStructureGroup feeStructureGroup, FeeCategory feeCategory, BigDecimal amount,
			PaymentFrequency paymentFrequency, BigDecimal oneShotDiscountAmount) {
		this.feeStructureGroup = feeStructureGroup;
		this.feeCategory = feeCategory;
		this.amount = amount;
		this.status = FeeStructureStatus.ACTIVE;
		this.paymentFrequency = paymentFrequency;
		this.oneShotDiscountAmount = oneShotDiscountAmount;
	}
}
