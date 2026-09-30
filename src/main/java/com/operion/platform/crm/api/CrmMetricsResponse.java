package com.operion.platform.crm.api;

import java.util.Map;

import com.operion.platform.crm.CrmMetricsService;

public record CrmMetricsResponse(Map<String, Long> leadsByStage, long wonCount, long lostCount, Double winRate,
		long openTaskCount, long overdueTaskCount) {

	public static CrmMetricsResponse from(CrmMetricsService.Metrics metrics) {
		Map<String, Long> byStage = new java.util.LinkedHashMap<>();
		metrics.leadsByStage().forEach((stage, count) -> byStage.put(stage.name(), count));
		return new CrmMetricsResponse(byStage, metrics.wonCount(), metrics.lostCount(), metrics.winRate(),
				metrics.openTaskCount(), metrics.overdueTaskCount());
	}
}
