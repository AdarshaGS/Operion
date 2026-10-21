import type { AppraisalCycle, ReviewStatus, StaffRow } from "../../api/performance";

export const REVIEW_STATUS_LABEL: Record<ReviewStatus, string> = {
	PENDING: "Pending",
	REVIEWED: "Reviewed",
	FINALIZED: "Finalized",
	NOT_REVIEWED: "Not reviewed",
};

export const REVIEW_STATUS_HINT: Record<ReviewStatus, string> = {
	PENDING: "No manager rating has been recorded yet.",
	REVIEWED: "A manager rating is recorded. It can still be revised until the cycle is closed.",
	FINALIZED: "Reviewed, and the cycle is closed so ratings can no longer change.",
	NOT_REVIEWED: "The cycle closed without a manager rating.",
};

export const REVIEW_STATUSES: ReviewStatus[] = ["PENDING", "REVIEWED", "FINALIZED", "NOT_REVIEWED"];

export interface StaffFilters {
	search: string;
	department: string;
	designation: string;
	status: ReviewStatus | "";
}

export const NO_FILTERS: StaffFilters = { search: "", department: "", designation: "", status: "" };

/** Sentinel for "staff with no department / designation recorded", so they can be filtered like any other value. */
export const NOT_SET = "__not_set__";

export function hasFilters(filters: StaffFilters): boolean {
	return filters.search.trim() !== "" || filters.department !== "" || filters.designation !== "" || filters.status !== "";
}

/** Case-insensitive match on name or employee ID, plus exact department / designation / status. */
export function filterStaff(rows: StaffRow[], filters: StaffFilters): StaffRow[] {
	const needle = filters.search.trim().toLowerCase();
	return rows.filter((row) => {
		if (needle && !row.name.toLowerCase().includes(needle) && !(row.employeeCode ?? "").toLowerCase().includes(needle)) {
			return false;
		}
		if (filters.department && (row.department ?? NOT_SET) !== filters.department) {
			return false;
		}
		if (filters.designation && (row.designation ?? NOT_SET) !== filters.designation) {
			return false;
		}
		return !filters.status || row.reviewStatus === filters.status;
	});
}

/** Distinct values of a column, alphabetical, with the "not set" sentinel last when some rows lack the value. */
export function distinctValues(rows: StaffRow[], pick: (row: StaffRow) => string | null): string[] {
	const values = new Set<string>();
	let missing = false;
	for (const row of rows) {
		const value = pick(row);
		if (value) {
			values.add(value);
		} else {
			missing = true;
		}
	}
	const sorted = [...values].sort((a, b) => a.localeCompare(b));
	return missing ? [...sorted, NOT_SET] : sorted;
}

export function formatPercent(value: number | null | undefined): string {
	return value === null || value === undefined ? "—" : `${value.toFixed(1)}%`;
}

export function formatRating(value: number | null | undefined): string {
	return value === null || value === undefined ? "—" : `${value.toFixed(1)} / 5`;
}

/** Whole date, locale style; "—" for a missing timestamp. */
export function formatDate(value: string | null | undefined): string {
	if (!value) {
		return "—";
	}
	const date = new Date(value.length === 10 ? `${value}T00:00:00` : value);
	return Number.isNaN(date.getTime()) ? "—" : date.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

export function formatPeriod(from: string, to: string): string {
	return `${formatDate(from)} – ${formatDate(to)}`;
}

/** Where to show a missing profile field, instead of an empty cell. */
export function orNotSet(value: string | null | undefined): string {
	return value && value.trim() ? value : "Not set";
}

/** The cycle to show: the requested one if it exists, else the open one, else the latest. Selection lives in the URL (?cycle=) so it survives a trip to a staff appraisal. */
export function pickCycle(cycles: AppraisalCycle[], requested: number | null): AppraisalCycle | null {
	return cycles.find((c) => c.id === requested) ?? cycles.find((c) => c.status === "OPEN") ?? cycles[0] ?? null;
}
