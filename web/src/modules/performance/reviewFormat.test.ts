import { describe, expect, it } from "vitest";
import type { AppraisalCycle, StaffRow } from "../../api/performance";
import { distinctValues, filterStaff, formatDate, formatPercent, NO_FILTERS, NOT_SET, orNotSet, pickCycle } from "./reviewFormat";

function row(over: Partial<StaffRow>): StaffRow {
	return {
		personId: 1,
		name: "Asha Nair",
		employeeCode: "EMP-001",
		designation: "Teacher",
		department: "Science",
		presencePercent: null,
		overallScore: null,
		reviewStatus: "PENDING",
		managerRating: null,
		selfAppraisalSubmitted: false,
		goalCount: 0,
		goalsAchieved: 0,
		lastUpdated: null,
		...over,
	};
}

const rows = [
	row({ personId: 1 }),
	row({ personId: 2, name: "Ben Kurian", employeeCode: "EMP-002", department: null, designation: "Senior Teacher", reviewStatus: "REVIEWED" }),
	row({ personId: 3, name: "Chitra Rao", employeeCode: null, department: "Maths", reviewStatus: "FINALIZED" }),
];

describe("filterStaff", () => {
	it("returns everyone when no filter is set", () => {
		expect(filterStaff(rows, NO_FILTERS)).toHaveLength(3);
	});

	it("searches name and employee ID case-insensitively", () => {
		expect(filterStaff(rows, { ...NO_FILTERS, search: "ben" }).map((r) => r.personId)).toEqual([2]);
		expect(filterStaff(rows, { ...NO_FILTERS, search: "emp-001" }).map((r) => r.personId)).toEqual([1]);
		expect(filterStaff(rows, { ...NO_FILTERS, search: "  " })).toHaveLength(3);
	});

	it("does not crash on a missing employee ID", () => {
		expect(filterStaff(rows, { ...NO_FILTERS, search: "emp" }).map((r) => r.personId)).toEqual([1, 2]);
	});

	it("filters by department, including staff with none", () => {
		expect(filterStaff(rows, { ...NO_FILTERS, department: "Maths" }).map((r) => r.personId)).toEqual([3]);
		expect(filterStaff(rows, { ...NO_FILTERS, department: NOT_SET }).map((r) => r.personId)).toEqual([2]);
	});

	it("filters by designation and review status, and combines filters", () => {
		expect(filterStaff(rows, { ...NO_FILTERS, designation: "Senior Teacher" }).map((r) => r.personId)).toEqual([2]);
		expect(filterStaff(rows, { ...NO_FILTERS, status: "FINALIZED" }).map((r) => r.personId)).toEqual([3]);
		expect(filterStaff(rows, { ...NO_FILTERS, status: "REVIEWED", department: "Science" })).toHaveLength(0);
	});
});

describe("distinctValues", () => {
	it("lists values alphabetically with the not-set sentinel last", () => {
		expect(distinctValues(rows, (r) => r.department)).toEqual(["Maths", "Science", NOT_SET]);
		expect(distinctValues(rows, (r) => r.designation)).toEqual(["Senior Teacher", "Teacher"]);
	});
});

describe("formatters", () => {
	it("shows a dash for absent values rather than zero", () => {
		expect(formatPercent(null)).toBe("—");
		expect(formatPercent(0)).toBe("0.0%");
		expect(formatDate(null)).toBe("—");
		expect(formatDate("not a date")).toBe("—");
		expect(orNotSet(null)).toBe("Not set");
		expect(orNotSet("  ")).toBe("Not set");
		expect(orNotSet("Science")).toBe("Science");
	});
});

describe("pickCycle", () => {
	const closed: AppraisalCycle = { id: 1, name: "Term 1", startDate: "2026-01-01", endDate: "2026-03-31", status: "CLOSED" };
	const open: AppraisalCycle = { id: 2, name: "Term 2", startDate: "2026-04-01", endDate: "2026-06-30", status: "OPEN" };

	it("prefers the requested cycle, then the open one, then the first", () => {
		expect(pickCycle([open, closed], 1)?.id).toBe(1);
		expect(pickCycle([closed, open], null)?.id).toBe(2);
		expect(pickCycle([closed], 99)?.id).toBe(1);
		expect(pickCycle([], null)).toBeNull();
	});
});
