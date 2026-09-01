package com.operion.examination;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Org-wide examination policy: whether subject-wise/class-wise ranking is computed
 * (#136), and how overall pass/fail is decided (#135, configurable per organisation since
 * schools vary on what "overall pass" means). Lazy, at most one row per organisation -
 * same "no row until first PUT, defaults on GET" convention as DocumentTemplate, kept in
 * the examination module rather than OrganisationConfiguration since "ranking" and
 * exam pass/fail policy are School-specific vocabulary that shouldn't leak into the
 * generic org-wide settings table (same boundary reasoning as #75/#82).
 */
@Getter
@Setter
@Entity
@Table(name = "examination_settings")
public class ExaminationSettings extends TenantScopedEntity {

	public static final boolean DEFAULT_RANKING_ENABLED = false;
	public static final PassFailStrategy DEFAULT_PASS_FAIL_STRATEGY = PassFailStrategy.PASS_EVERY_SUBJECT;
	public static final double DEFAULT_MINIMUM_AGGREGATE_PERCENTAGE = 33.0;

	@Column(name = "ranking_enabled", nullable = false)
	private boolean rankingEnabled;

	@Enumerated(EnumType.STRING)
	@Column(name = "pass_fail_strategy", nullable = false, length = 30)
	private PassFailStrategy passFailStrategy;

	@Column(name = "minimum_aggregate_percentage", nullable = false)
	private double minimumAggregatePercentage;

	public ExaminationSettings() {
		this.rankingEnabled = DEFAULT_RANKING_ENABLED;
		this.passFailStrategy = DEFAULT_PASS_FAIL_STRATEGY;
		this.minimumAggregatePercentage = DEFAULT_MINIMUM_AGGREGATE_PERCENTAGE;
	}
}
