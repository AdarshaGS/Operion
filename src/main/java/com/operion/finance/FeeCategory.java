package com.operion.finance;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Org-scoped catalog (Tuition, Transport, Lab...), per ai-context/erp-system-plan.md §3.2. */
@Getter
@Entity
@Table(name = "fee_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeCategory extends TenantScopedEntity {

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	/** Nullable. */
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FeeCategoryStatus status;

	/** Distinguishes Transport (and other typed) categories from general ones, so
	 * category-specific flows (e.g. transport assignment) can filter to the right fee
	 * structures instead of matching on free-text name/code. Per #285. */
	@Enumerated(EnumType.STRING)
	@Column(name = "category_type", nullable = false, length = 20)
	private FeeCategoryType categoryType;

	/** When true, a student's first instalment of this category's fee structure is assigned and invoiced
	 * at enrolment and gates their admission-fee clearance (see AdmissionFeeService). */
	@Column(name = "required_at_admission", nullable = false)
	private boolean requiredAtAdmission;

	public FeeCategory(String code, String name, String description, FeeCategoryType categoryType, boolean requiredAtAdmission) {
		this(code, name, description, categoryType);
		this.requiredAtAdmission = requiredAtAdmission;
	}

	public FeeCategory(String code, String name, String description, FeeCategoryType categoryType) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.status = FeeCategoryStatus.ACTIVE;
		this.categoryType = categoryType;
	}

	public void setRequiredAtAdmission(boolean requiredAtAdmission) {
		this.requiredAtAdmission = requiredAtAdmission;
	}
}
