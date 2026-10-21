package com.operion.performance;

import java.util.Map;
import java.util.Set;

/**
 * Computes one or more metrics from existing records. A teacher with no relevant records in the period
 * is simply absent from the result - "no data" is not the same as 0.
 */
public interface KpiMetricProvider {

	Set<KpiMetric> supports();

	/** Value per teacher person id. {@code days} is the definition's day threshold, null for metrics that do not use one. */
	Map<Long, MetricValue> compute(KpiMetric metric, KpiPeriod period, Integer days);
}
