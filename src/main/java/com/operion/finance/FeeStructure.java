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

	public FeeStructure(FeeStructureGroup feeStructureGroup, FeeCategory feeCategory, BigDecimal amount) {
		this.feeStructureGroup = feeStructureGroup;
		this.feeCategory = feeCategory;
		this.amount = amount;
		this.status = FeeStructureStatus.ACTIVE;
	}
}
