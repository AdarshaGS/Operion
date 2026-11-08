import { describe, expect, it } from "vitest";
import type { PromotionPreviewClass } from "../../api/promotions";
import { applyReadiness, buildDecisions, countChoices, filterPreviewByCampus, initialRowState, type PromotionContext, type RowStates } from "./promotionWizard";

function cls(id: number, studentIds: number[]): PromotionPreviewClass {
	return {
		sourceSchoolClassId: id,
		label: `Class ${id}`,
		proposedTargetSchoolClassId: id + 100,
		proposedAction: "PROMOTE",
		reason: null,
		targetSections: [{ id: id * 10, name: "A", remainingCapacity: null }],
		students: studentIds.map((studentId) => ({
			studentId,
			name: `Student ${studentId}`,
			admissionNumber: `A${studentId}`,
			currentSectionId: 1,
			proposedAction: "PROMOTE" as const,
			reason: "",
			suggestedTargetSectionId: id * 10,
		})),
	};
}

const MAIN = cls(1, [11, 12]);
const BRANCH = cls(2, [21]);
const PREVIEW = [MAIN, BRANCH];
const CAMPUSES = { 1: 100, 2: 200 };
const CONTEXT: PromotionContext = { repeatSections: {}, sectionNames: {} };

function allRows(): RowStates {
	const rows: RowStates = {};
	for (const c of PREVIEW) for (const s of c.students) rows[s.studentId] = initialRowState(s, c, CONTEXT, true);
	return rows;
}

describe("filterPreviewByCampus", () => {
	it("keeps only the classes of the picked campus", () => {
		expect(filterPreviewByCampus(PREVIEW, CAMPUSES, 100)).toEqual([MAIN]);
		expect(filterPreviewByCampus(PREVIEW, CAMPUSES, 200)).toEqual([BRANCH]);
	});

	it("keeps every class under All campuses", () => {
		expect(filterPreviewByCampus(PREVIEW, CAMPUSES, null)).toBe(PREVIEW);
	});

	it("leaves out a class whose campus is unknown once a campus is picked", () => {
		expect(filterPreviewByCampus(PREVIEW, { 1: 100 }, 100)).toEqual([MAIN]);
		expect(filterPreviewByCampus(PREVIEW, {}, 100)).toEqual([]);
	});
});

describe("a campus-filtered plan feeds Apply", () => {
	it("sends and counts only the picked campus's students", () => {
		const visible = filterPreviewByCampus(PREVIEW, CAMPUSES, 100);
		const rows = allRows();

		expect(buildDecisions(visible, rows).map((d) => d.studentId)).toEqual([11, 12]);
		expect(countChoices(visible, rows).promote).toBe(2);
		expect(applyReadiness(visible, rows, CONTEXT).toApply).toBe(2);
	});

	it("sends every campus's students under All campuses", () => {
		const visible = filterPreviewByCampus(PREVIEW, CAMPUSES, null);
		expect(buildDecisions(visible, allRows()).map((d) => d.studentId)).toEqual([11, 12, 21]);
	});
});
