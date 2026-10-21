package com.operion.performance.api;

import java.math.BigDecimal;

import com.operion.performance.KpiService;

public record KpiDefinitionResponse(String metric, String label, String description, String unit, String kind, boolean usesDays,
		int maxTarget, boolean enabled, int weight, BigDecimal target, Integer days) {

	public static KpiDefinitionResponse from(KpiService.Definition d) {
		return new KpiDefinitionResponse(d.metric().name(), d.metric().label(), d.metric().description(), d.metric().unit().name(),
				d.metric().kind().name(), d.metric().usesDays(), d.metric().maxTarget(), d.enabled(), d.weight(), d.target(), d.paramDays());
	}
}
