import type { KpiMetricConfig, KpiUnit } from "../../api/performance";

export function isoDate(date: Date): string {
	return date.toISOString().slice(0, 10);
}

export function defaultFrom(): string {
	const date = new Date();
	date.setDate(date.getDate() - 29);
	return isoDate(date);
}

export function formatValue(value: number | null, unit: KpiUnit): string {
	if (value === null) {
		return "—";
	}
	return unit === "PERCENT" ? `${value.toFixed(1)}%` : `${value.toFixed(1)} / 30 days`;
}

export function formatScore(score: number | null): string {
	return score === null ? "—" : `${score.toFixed(1)}%`;
}

/** What the metric is scored against, for a column heading. */
export function targetLabel(metric: KpiMetricConfig): string {
	if (metric.kind === "OUTCOME") {
		return "vs previous period";
	}
	return metric.unit === "PERCENT" ? `target ${metric.target}%` : `target ${metric.target} / 30 days`;
}
