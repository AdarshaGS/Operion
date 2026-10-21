package com.operion.performance;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Org-wide switches for the performance module. Lazy, at most one row per organisation; everything defaults to off. */
@Getter
@Setter
@Entity
@Table(name = "kpi_settings")
public class KpiSettings extends TenantScopedEntity {

	/** Whether teachers can see their own scorecard and appraisal. */
	@Column(name = "teacher_self_view", nullable = false)
	private boolean teacherSelfView;
}
