package com.operion.finance;

import java.math.BigDecimal;

import com.operion.academic.SchoolClass;
import com.operion.common.TenantScopedEntity;
import com.operion.organisation.AcademicYear;
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
 * Amount of one FeeCategory for one SchoolClass in one AcademicYear - an explicit row per
 * class, not a nullable "applies to all classes" wildcard, which would need
 * fallback/precedence resolution at read time. Per ai-context/erp-system-plan.md §3.2.
 */
@Getter
@Entity
@Table(name = "fee_structures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeStructure extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@ManyToOne(optional = false)
	@JoinColumn(name = "school_class_id")
	private SchoolClass schoolClass;

	@ManyToOne(optional = false)
	@JoinColumn(name = "fee_category_id")
	private FeeCategory feeCategory;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FeeStructureStatus status;

	public FeeStructure(AcademicYear academicYear, SchoolClass schoolClass, FeeCategory feeCategory, BigDecimal amount) {
		this.academicYear = academicYear;
		this.schoolClass = schoolClass;
		this.feeCategory = feeCategory;
		this.amount = amount;
		this.status = FeeStructureStatus.ACTIVE;
	}
}
