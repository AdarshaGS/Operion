import { describe, expect, it } from "vitest";
import type { PromotionOutcome, PromotionPreviewClass, PromotionPreviewStudent } from "../../api/promotions";
import {
	applyReadiness,
	buildClassMap,
	buildDecisions,
	classTargetValue,
	countChoices,
	exceptionBlocker,
	hasNoTarget,
	initialRowState,
	mergeOutcomes,
	reconcileRowStates,
	setAllChoices,
	summariseOutcomes,
	withChoice,
	type PromotionContext,
	type RowStates,
} from "./promotionWizard";

function student(id: number, proposedAction: PromotionPreviewStudent["proposedAction"], overrides: Partial<PromotionPreviewStudent> = {}): PromotionPreviewStudent {
	return {
		studentId: id,
		name: `Student ${id}`,
		admissionNumber: `A${id}`,
		currentSectionId: 100,
		proposedAction,
		reason: "",
		suggestedTargetSectionId: null,
		...overrides,
	};
}

function cls(overrides: Partial<PromotionPreviewClass> = {}): PromotionPreviewClass {
	return {
		sourceSchoolClassId: 1,
		label: "Grade 1",
		proposedTargetSchoolClassId: 2,
		proposedAction: "PROMOTE",
		reason: null,
		targetSections: [
			{ id: 20, name: "A", remainingCapacity: 5 },
			{ id: 21, name: "B", remainingCapacity: 0 },
		],
		students: [],
		...overrides,
	};
}

const context: PromotionContext = {
	repeatSections: { 1: [{ id: 10, name: "A", remainingCapacity: undefined }] },
	sectionNames: { 100: "A" },
};

describe("class plan helpers", () => {
	it("builds a classMap from edited rows, null meaning graduate", () => {
		expect(buildClassMap({ 1: 5, 2: null })).toEqual([
			{ sourceSchoolClassId: 1, targetSchoolClassId: 5 },
			{ sourceSchoolClassId: 2, targetSchoolClassId: null },
		]);
		expect(buildClassMap({})).toEqual([]);
	});

	it("derives the target select value and the no-target warning from the response", () => {
		expect(classTargetValue(cls())).toBe("2");
		expect(classTargetValue(cls({ proposedAction: "GRADUATE", proposedTargetSchoolClassId: null }))).toBe("graduate");
		const none = cls({ proposedTargetSchoolClassId: null, reason: "no Grade 2 class in target year" });
		expect(classTargetValue(none)).toBe("");
		expect(hasNoTarget(none)).toBe(true);
		expect(hasNoTarget(cls())).toBe(false);
		expect(hasNoTarget(cls({ proposedAction: "GRADUATE", proposedTargetSchoolClassId: null }))).toBe(false);
	});
});

describe("initialRowState", () => {
	const c = cls();
	it("preselects the proposal and the suggested section", () => {
		expect(initialRowState(student(1, "PROMOTE", { suggestedTargetSectionId: 20 }), c, context, true)).toEqual({ choice: "PROMOTE", sectionId: 20 });
		expect(initialRowState(student(1, "GRADUATE"), c, context, true)).toEqual({ choice: "GRADUATE", sectionId: null });
	});

	it("defaults an exception to Promote with the suggested section", () => {
		expect(initialRowState(student(1, "NEEDS_DECISION", { suggestedTargetSectionId: 20 }), c, context, true)).toEqual({ choice: "PROMOTE", sectionId: 20 });
		expect(initialRowState(student(1, "NEEDS_DECISION"), c, context, true)).toEqual({ choice: "PROMOTE", sectionId: null });
	});

	it("defaults an exception in a class with no next grade to Graduate", () => {
		const top = cls({ proposedAction: "GRADUATE", proposedTargetSchoolClassId: null, targetSections: [] });
		expect(initialRowState(student(1, "NEEDS_DECISION"), top, context, true)).toEqual({ choice: "GRADUATE", sectionId: null });
		// Without the exit permission there is no safe default, so it stays undecided and says why.
		expect(initialRowState(student(1, "NEEDS_DECISION"), top, context, false).choice).toBeNull();
		expect(exceptionBlocker(top, false)).toMatch(/permission/);
	});

	it("leaves an exception undecided when its class has nowhere to promote to", () => {
		const noTarget = cls({ proposedTargetSchoolClassId: null, targetSections: [] });
		expect(initialRowState(student(1, "NEEDS_DECISION"), noTarget, context, true)).toEqual({ choice: null, sectionId: null });
		expect(exceptionBlocker(noTarget, true)).toMatch(/No target class/);
		const noSections = cls({ targetSections: [] });
		expect(initialRowState(student(1, "NEEDS_DECISION"), noSections, context, true).choice).toBeNull();
		expect(exceptionBlocker(noSections, true)).toMatch(/no sections/);
		expect(exceptionBlocker(cls(), true)).toBeNull();
	});

	it("skips already-done students, and graduates without the exit permission", () => {
		expect(initialRowState(student(1, "ALREADY_DONE"), c, context, true).choice).toBe("SKIP");
		expect(initialRowState(student(1, "GRADUATE"), c, context, false).choice).toBe("SKIP");
	});

	it("ignores a suggested section that is not a target option", () => {
		expect(initialRowState(student(1, "PROMOTE", { suggestedTargetSectionId: 999 }), c, context, true).sectionId).toBeNull();
	});
});

describe("withChoice", () => {
	const c = cls();
	const s = student(1, "NEEDS_DECISION", { suggestedTargetSectionId: 20 });

	it("switches a repeater to the same-named repeat section", () => {
		expect(withChoice({ choice: null, sectionId: 20 }, "REPEAT", s, c, context)).toEqual({ choice: "REPEAT", sectionId: 10 });
	});

	it("keeps a section the admin picked while it is still valid, clears it for graduate/skip", () => {
		expect(withChoice({ choice: null, sectionId: 21 }, "PROMOTE", s, c, context)).toEqual({ choice: "PROMOTE", sectionId: 21 });
		expect(withChoice({ choice: "PROMOTE", sectionId: 21 }, "SKIP", s, c, context)).toEqual({ choice: "SKIP", sectionId: null });
		expect(withChoice({ choice: "PROMOTE", sectionId: 21 }, "GRADUATE", s, c, context).sectionId).toBeNull();
	});
});

describe("applyReadiness", () => {
	const preview = [cls({ students: [student(1, "PROMOTE"), student(2, "NEEDS_DECISION"), student(3, "ALREADY_DONE")] })];

	it("blocks while an exception is undecided", () => {
		const rows: RowStates = { 1: { choice: "PROMOTE", sectionId: 20 }, 2: { choice: null, sectionId: null }, 3: { choice: "SKIP", sectionId: null } };
		expect(applyReadiness(preview, rows, context)).toMatchObject({ undecided: 1, missingSection: 0, toApply: 1, ready: false });
	});

	it("blocks a promote or repeat row without a section, but not a skipped or graduating one", () => {
		const rows: RowStates = { 1: { choice: "PROMOTE", sectionId: null }, 2: { choice: "GRADUATE", sectionId: null }, 3: { choice: "SKIP", sectionId: null } };
		expect(applyReadiness(preview, rows, context)).toMatchObject({ missingSection: 1, toApply: 2, ready: false });
		rows[1] = { choice: "SKIP", sectionId: null };
		expect(applyReadiness(preview, rows, context)).toMatchObject({ missingSection: 0, toApply: 1, ready: true });
	});

	it("treats a section that is not an option for the chosen action as missing", () => {
		const rows: RowStates = { 1: { choice: "REPEAT", sectionId: 20 }, 2: { choice: "SKIP", sectionId: null }, 3: { choice: "SKIP", sectionId: null } };
		expect(applyReadiness(preview, rows, context).missingSection).toBe(1);
	});

	it("is not ready when everything is skipped", () => {
		const rows: RowStates = { 1: { choice: "SKIP", sectionId: null }, 2: { choice: "SKIP", sectionId: null }, 3: { choice: "SKIP", sectionId: null } };
		expect(applyReadiness(preview, rows, context)).toMatchObject({ toApply: 0, ready: false });
	});

	it("ignores students an earlier run already moved", () => {
		const rows: RowStates = { 1: { choice: "PROMOTE", sectionId: null }, 2: { choice: "SKIP", sectionId: null }, 3: { choice: "SKIP", sectionId: null } };
		const outcomes = { 1: { studentId: 1, action: "PROMOTE", succeeded: true, skipped: false, error: null } } as Record<number, PromotionOutcome>;
		expect(applyReadiness(preview, rows, context, outcomes).missingSection).toBe(0);
	});
});

describe("buildDecisions", () => {
	const preview = [cls({ students: [student(1, "PROMOTE"), student(2, "NEEDS_DECISION"), student(3, "GRADUATE"), student(4, "ALREADY_DONE")] })];
	const rows: RowStates = {
		1: { choice: "PROMOTE", sectionId: 20 },
		2: { choice: "REPEAT", sectionId: 10 },
		3: { choice: "GRADUATE", sectionId: 20 },
		4: { choice: "SKIP", sectionId: null },
	};

	it("sends promote, repeat and graduate decisions without roll numbers and drops skipped rows", () => {
		expect(buildDecisions(preview, rows)).toEqual([
			{ studentId: 1, action: "PROMOTE", targetSectionId: 20, rollNumber: null },
			{ studentId: 2, action: "REPEAT", targetSectionId: 10, rollNumber: null },
			{ studentId: 3, action: "GRADUATE", targetSectionId: null, rollNumber: null },
		]);
	});

	it("retries only the failed students", () => {
		const outcomes = {
			1: { studentId: 1, action: "PROMOTE", succeeded: true, skipped: false, error: null },
			2: { studentId: 2, action: "REPEAT", succeeded: false, skipped: false, error: "Section is full" },
		} as Record<number, PromotionOutcome>;
		expect(buildDecisions(preview, rows, outcomes, new Set([2])).map((d) => d.studentId)).toEqual([2]);
		// Without a filter an earlier success is still never re-sent.
		expect(buildDecisions(preview, rows, outcomes).map((d) => d.studentId)).toEqual([2, 3]);
	});
});

describe("reconcileRowStates", () => {
	it("keeps choices of unchanged classes and restarts the class whose target changed", () => {
		const before = [
			cls({ sourceSchoolClassId: 1, students: [student(1, "PROMOTE", { suggestedTargetSectionId: 20 })] }),
			cls({ sourceSchoolClassId: 3, proposedTargetSchoolClassId: null, students: [student(5, "NEEDS_DECISION")] }),
		];
		const prevRows: RowStates = { 1: { choice: "REPEAT", sectionId: 10 }, 5: { choice: "SKIP", sectionId: null } };
		const after = [
			before[0],
			cls({ sourceSchoolClassId: 3, proposedTargetSchoolClassId: 9, students: [student(5, "NEEDS_DECISION", { suggestedTargetSectionId: 20 })] }),
		];
		const next = reconcileRowStates(prevRows, before, after, context, true);
		expect(next[1]).toEqual({ choice: "REPEAT", sectionId: 10 });
		// The class that gained a target restarts from the new proposal: the exception now defaults to Promote.
		expect(next[5]).toEqual({ choice: "PROMOTE", sectionId: 20 });
	});
});

describe("setAllChoices and countChoices", () => {
	const noTarget = cls({ sourceSchoolClassId: 3, proposedTargetSchoolClassId: null, targetSections: [], students: [student(5, "NEEDS_DECISION")] });
	const preview = [
		cls({ students: [student(1, "PROMOTE", { suggestedTargetSectionId: 20 }), student(2, "NEEDS_DECISION", { suggestedTargetSectionId: 20 }), student(3, "ALREADY_DONE")] }),
		noTarget,
	];
	const start = (): RowStates =>
		Object.fromEntries(preview.flatMap((c) => c.students.map((s) => [s.studentId, initialRowState(s, c, context, true)])));

	it("tallies the starting decisions, leaving already-done students out of the choices", () => {
		expect(countChoices(preview, start())).toEqual({ promote: 2, repeat: 0, graduate: 0, skip: 0, undecided: 1, alreadyDone: 1 });
	});

	it("sets Promote only on rows that have a target class, never on already-done rows", () => {
		const rows = { ...start(), 1: { choice: "SKIP" as const, sectionId: null } };
		const next = setAllChoices(preview, rows, "PROMOTE", context, {}, true);
		expect(next[1]).toEqual({ choice: "PROMOTE", sectionId: 20 });
		expect(next[3]).toEqual({ choice: "SKIP", sectionId: null });
		expect(next[5]).toEqual({ choice: null, sectionId: null });
		expect(countChoices(preview, next)).toMatchObject({ promote: 2, undecided: 1 });
	});

	it("sets Skip and Repeat on every eligible row, including the one with no target", () => {
		expect(countChoices(preview, setAllChoices(preview, start(), "SKIP", context, {}, true))).toMatchObject({ skip: 3, promote: 0, undecided: 0 });
		const repeated = setAllChoices(preview, start(), "REPEAT", context, {}, true);
		expect(repeated[1]).toEqual({ choice: "REPEAT", sectionId: 10 });
		expect(repeated[3]).toEqual({ choice: "SKIP", sectionId: null });
	});

	it("does not graduate anyone without the exit permission, and skips students an earlier run moved", () => {
		const rows = start();
		expect(setAllChoices(preview, rows, "GRADUATE", context, {}, false)).toEqual(rows);
		const outcomes = { 1: { studentId: 1, action: "PROMOTE", succeeded: true, skipped: false, error: null } } as Record<number, PromotionOutcome>;
		const next = setAllChoices(preview, rows, "SKIP", context, outcomes, true);
		expect(next[1]).toEqual(rows[1]);
		expect(countChoices(preview, next, outcomes)).toMatchObject({ promote: 0, skip: 2 });
	});
});

describe("outcomes", () => {
	const o = (studentId: number, succeeded: boolean, skipped: boolean): PromotionOutcome => ({
		studentId,
		action: "PROMOTE",
		succeeded,
		skipped,
		error: succeeded ? null : "boom",
	});

	it("counts succeeded, already-applied and failed separately", () => {
		expect(summariseOutcomes([o(1, true, false), o(2, true, true), o(3, false, false), o(4, true, false)])).toEqual({ succeeded: 2, skipped: 1, failed: 1 });
	});

	it("lets a retry replace the earlier outcome of the same student", () => {
		const merged = mergeOutcomes({ 1: o(1, true, false), 3: o(3, false, false) }, [o(3, true, false)]);
		expect(summariseOutcomes(Object.values(merged))).toEqual({ succeeded: 2, skipped: 0, failed: 0 });
	});
});
