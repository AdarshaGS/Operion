package com.operion.performance;

/** A teacher's measured value for one metric over a period (a percentage, or a count per 30 days), and how many records it rests on. */
public record MetricValue(double value, int sample) {

	/** Percentage of {@code part} in {@code whole}; the caller guarantees whole is positive. */
	public static MetricValue ratio(int part, int whole) {
		return new MetricValue(100.0 * part / whole, whole);
	}

	/** {@code count} scaled to a 30-day month so periods of different lengths compare. */
	public static MetricValue perThirtyDays(int count, KpiPeriod period) {
		return new MetricValue(count * 30.0 / period.days(), count);
	}
}
