package com.operion.performance;

import java.math.BigDecimal;

import com.operion.common.InvalidRequestException;
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
 * One organisation's choice for one built-in metric: tracked or not, how much it weighs against the
 * others, the value that earns full marks (a percentage, or a count per 30 days) and, for metrics that
 * need one, a day threshold. A metric with no row is not tracked.
 */
@Getter
@Entity
@Table(name = "kpi_definitions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KpiDefinition extends TenantScopedEntity {

	public static final int DEFAULT_WEIGHT = 1;
	private static final int MAX_DAYS = 90;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private KpiMetric metric;

	@Column(nullable = false)
	private boolean enabled;

	@Column(nullable = false)
	private int weight;

	@Column(nullable = false)
	private BigDecimal target;

	@Column(name = "param_days")
	private Integer paramDays;

	public KpiDefinition(KpiMetric metric) {
		this.metric = metric;
		this.enabled = false;
		this.weight = DEFAULT_WEIGHT;
		this.target = BigDecimal.valueOf(metric.defaultTarget());
		this.paramDays = metric.defaultDays();
	}

	public void configure(boolean enabled, int weight, BigDecimal target, Integer paramDays) {
		if (weight < 1 || weight > 100) {
			throw new InvalidRequestException("The weight must be between 1 and 100");
		}
		if (target.signum() <= 0 || target.compareTo(BigDecimal.valueOf(metric.maxTarget())) > 0) {
			throw new InvalidRequestException("The target for " + metric.label() + " must be above 0 and at most " + metric.maxTarget());
		}
		if (metric.usesDays() && (paramDays == null || paramDays < 0 || paramDays > MAX_DAYS)) {
			throw new InvalidRequestException("The number of days must be between 0 and " + MAX_DAYS);
		}
		this.enabled = enabled;
		this.weight = weight;
		this.target = target;
		this.paramDays = metric.usesDays() ? paramDays : null;
	}
}
