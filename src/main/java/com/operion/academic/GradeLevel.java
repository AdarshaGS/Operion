package com.operion.academic;

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
 * Catalog of grades a school can offer (Nursery...Grade 12), org-scoped but NOT
 * year-scoped, per ai-context/erp-system-plan.md §2.1 - decoupled from year instances
 * (SchoolClass) so promotion logic has a stable ordering to walk.
 */
@Getter
@Entity
@Table(name = "grade_levels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeLevel extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "sequence_order", nullable = false)
	private int sequenceOrder;

	/** Nullable - optional grouping like "Primary"/"Secondary". */
	private String stage;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GradeLevelStatus status;

	public GradeLevel(String name, int sequenceOrder, String stage) {
		this.name = name;
		this.sequenceOrder = sequenceOrder;
		this.stage = stage;
		this.status = GradeLevelStatus.ACTIVE;
	}
}
