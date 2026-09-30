import type { LeadStage } from "../api/crm";

export const STAGE_LABEL: Record<LeadStage, string> = {
	NEW: "New",
	CONTACTED: "Contacted",
	DEMO_SCHEDULED: "Demo scheduled",
	PROPOSAL: "Proposal",
	WON: "Won",
	LOST: "Lost",
};

export const STAGE_COLOR: Record<LeadStage, "default" | "info" | "warning" | "success" | "error" | "secondary"> = {
	NEW: "default",
	CONTACTED: "info",
	DEMO_SCHEDULED: "secondary",
	PROPOSAL: "warning",
	WON: "success",
	LOST: "error",
};

export function titleCase(value: string): string {
	return value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, " ");
}

export function formatDateTime(iso: string): string {
	return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

/** Value for an <input type="datetime-local"> - local wall-clock time, no timezone suffix. */
export function toLocalInputValue(date: Date): string {
	const pad = (n: number) => String(n).padStart(2, "0");
	return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function tomorrowMorningInput(): string {
	const date = new Date();
	date.setDate(date.getDate() + 1);
	date.setHours(9, 0, 0, 0);
	return toLocalInputValue(date);
}

export function isOverdue(task: { dueAt: string; completedAt: string | null }): boolean {
	return !task.completedAt && new Date(task.dueAt).getTime() < Date.now();
}

export function isDueToday(task: { dueAt: string; completedAt: string | null }): boolean {
	if (task.completedAt) return false;
	const due = new Date(task.dueAt);
	const now = new Date();
	return due.toDateString() === now.toDateString();
}
