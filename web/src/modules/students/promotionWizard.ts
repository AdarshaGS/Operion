import type {
	ClassMapping,
	PromotionDecision,
	PromotionOutcome,
	PromotionPreviewClass,
	PromotionPreviewStudent,
} from "../../api/promotions";

/** What the admin decides for one student. SKIP leaves the student exactly where they are (nothing is sent). */
export type RowChoice = "PROMOTE" | "REPEAT" | "GRADUATE" | "SKIP";

/** `choice` is null only for an exception that cannot default (see exceptionBlocker) and is not decided yet. */
export interface RowState {
	choice: RowChoice | null;
	sectionId: number | null;
}

export type RowStates = Record<number, RowState>;

/** `remainingCapacity`: null = no limit, undefined = not known (repeat sections come from the plain section list). */
export interface SectionChoice {
	id: number;
	name: string;
	remainingCapacity: number | null | undefined;
}

/** Page-level lookups the preview response does not carry. */
export interface PromotionContext {
	/** Source class id -> active sections of the same grade and campus in the target year (where a repeater goes). */
	repeatSections: Record<number, SectionChoice[]>;
	/** Source section id -> its name, to suggest the same-named section for a repeater. */
	sectionNames: Record<number, string>;
}

export const GRADUATE_TARGET = "graduate";

export interface PreviewRow {
	cls: PromotionPreviewClass;
	student: PromotionPreviewStudent;
}

/** Source class id -> the campus it belongs to; the preview response does not carry campuses. */
export type SourceClassCampuses = Record<number, number>;

/**
 * The class-plan rows for the campus picked in the top bar; null (All campuses) keeps every row. Everything the
 * wizard reads or sends (the student tables, the counts, Apply) works from this list, so students of a campus
 * that is not picked are neither shown nor promoted. A class whose campus is unknown is left out when a campus is picked.
 */
export function filterPreviewByCampus(
	preview: PromotionPreviewClass[],
	classCampuses: SourceClassCampuses,
	campusId: number | null,
): PromotionPreviewClass[] {
	if (campusId === null) return preview;
	return preview.filter((cls) => classCampuses[cls.sourceSchoolClassId] === campusId);
}

export function flattenStudents(preview: PromotionPreviewClass[]): PreviewRow[] {
	return preview.flatMap((cls) => cls.students.map((student) => ({ cls, student })));
}

/** Overrides the admin edited, in the shape the preview endpoint takes (null target = graduate the class). */
export function buildClassMap(overrides: Record<number, number | null>): ClassMapping[] {
	return Object.entries(overrides).map(([sourceId, target]) => ({
		sourceSchoolClassId: Number(sourceId),
		targetSchoolClassId: target,
	}));
}

/** Value of a class row's target select: a class id, GRADUATE_TARGET, or "" when the class has nowhere to go. */
export function classTargetValue(cls: PromotionPreviewClass): string {
	if (cls.proposedAction === "GRADUATE") return GRADUATE_TARGET;
	return cls.proposedTargetSchoolClassId == null ? "" : String(cls.proposedTargetSchoolClassId);
}

/** A class that promotes but has no class to promote into - its students cannot be promoted until one is picked. */
export function hasNoTarget(cls: PromotionPreviewClass): boolean {
	return cls.proposedAction === "PROMOTE" && cls.proposedTargetSchoolClassId == null;
}

export function sectionOptionsFor(choice: RowChoice | null, cls: PromotionPreviewClass, context: PromotionContext): SectionChoice[] {
	if (choice === "REPEAT") return context.repeatSections[cls.sourceSchoolClassId] ?? [];
	if (choice === "PROMOTE" || choice === null) return cls.targetSections;
	return [];
}

function sameNamed(options: SectionChoice[], name: string | undefined): number | null {
	if (!name) return null;
	return options.find((o) => o.name.toLowerCase() === name.toLowerCase())?.id ?? null;
}

/** The section a choice starts on: the server's suggestion for promote, the same-named section for repeat. */
export function defaultSectionId(
	choice: RowChoice | null,
	student: PromotionPreviewStudent,
	cls: PromotionPreviewClass,
	context: PromotionContext,
): number | null {
	if (choice === "REPEAT") {
		return sameNamed(sectionOptionsFor("REPEAT", cls, context), context.sectionNames[student.currentSectionId]);
	}
	if (choice === "PROMOTE" || choice === null) {
		const suggested = student.suggestedTargetSectionId;
		return suggested != null && cls.targetSections.some((s) => s.id === suggested) ? suggested : null;
	}
	return null;
}

/**
 * Why an exception cannot default to a decision, or null when it can: there is nowhere to promote to (no target
 * class, or a target class with no sections), or the class graduates but the user may not record exits.
 */
export function exceptionBlocker(cls: PromotionPreviewClass, canGraduate: boolean): string | null {
	if (cls.proposedAction === "GRADUATE") return canGraduate ? null : "Graduating needs the exit permission - choose another decision";
	if (hasNoTarget(cls)) return "No target class to promote into - set one in the class plan";
	if (cls.targetSections.length === 0) return "The target class has no sections yet";
	return null;
}

/**
 * Preselection from the proposal. Already-done students are skipped. An exception (NEEDS_DECISION) defaults to
 * Promote - or Graduate in a class with no next grade - and only stays undecided when it cannot (see exceptionBlocker).
 */
export function initialRowState(
	student: PromotionPreviewStudent,
	cls: PromotionPreviewClass,
	context: PromotionContext,
	canGraduate: boolean,
): RowState {
	switch (student.proposedAction) {
		case "ALREADY_DONE":
			return { choice: "SKIP", sectionId: null };
		case "NEEDS_DECISION":
			if (exceptionBlocker(cls, canGraduate) !== null) return { choice: null, sectionId: defaultSectionId(null, student, cls, context) };
			if (cls.proposedAction === "GRADUATE") return { choice: "GRADUATE", sectionId: null };
			return { choice: "PROMOTE", sectionId: defaultSectionId("PROMOTE", student, cls, context) };
		case "GRADUATE":
			// Graduating is an exit and needs its own permission; without it the row is left alone rather than failing the batch.
			return canGraduate ? { choice: "GRADUATE", sectionId: null } : { choice: "SKIP", sectionId: null };
		default:
			return { choice: student.proposedAction, sectionId: defaultSectionId(student.proposedAction, student, cls, context) };
	}
}

/** Switching the decision moves the section along: keep the pick when it is still a valid option, else the default. */
export function withChoice(
	state: RowState,
	choice: RowChoice,
	student: PromotionPreviewStudent,
	cls: PromotionPreviewClass,
	context: PromotionContext,
): RowState {
	if (choice === "GRADUATE" || choice === "SKIP") return { choice, sectionId: null };
	const options = sectionOptionsFor(choice, cls, context);
	const keep = state.sectionId != null && options.some((o) => o.id === state.sectionId);
	return { choice, sectionId: keep ? state.sectionId : defaultSectionId(choice, student, cls, context) };
}

/**
 * Row state for a fresh preview: a student keeps what the admin already chose when their class's plan did not
 * change; students of a class whose target changed start over from the new proposal.
 */
export function reconcileRowStates(
	prevRows: RowStates,
	prevPreview: PromotionPreviewClass[] | null,
	nextPreview: PromotionPreviewClass[],
	context: PromotionContext,
	canGraduate: boolean,
): RowStates {
	const next: RowStates = {};
	for (const cls of nextPreview) {
		const prevCls = prevPreview?.find((p) => p.sourceSchoolClassId === cls.sourceSchoolClassId);
		const unchanged =
			prevCls !== undefined &&
			prevCls.proposedAction === cls.proposedAction &&
			prevCls.proposedTargetSchoolClassId === cls.proposedTargetSchoolClassId;
		for (const student of cls.students) {
			const kept = prevRows[student.studentId];
			next[student.studentId] = unchanged && kept ? kept : initialRowState(student, cls, context, canGraduate);
		}
	}
	return next;
}

function succeededIds(outcomes: Record<number, PromotionOutcome>): Set<number> {
	return new Set(Object.values(outcomes).filter((o) => o.succeeded).map((o) => o.studentId));
}

/** Whether "set all to `choice`" may change this row: never for already-moved students; Promote needs a target class, Graduate the permission. */
export function canBulkSet(choice: RowChoice, row: PreviewRow, canGraduate: boolean): boolean {
	if (row.student.proposedAction === "ALREADY_DONE") return false;
	if (choice === "PROMOTE") return row.cls.proposedAction === "PROMOTE" && row.cls.proposedTargetSchoolClassId != null;
	if (choice === "GRADUATE") return canGraduate;
	return true;
}

/** Sets `choice` on every row that is eligible for it and returns the new states; every other row is returned untouched. */
export function setAllChoices(
	preview: PromotionPreviewClass[],
	rows: RowStates,
	choice: RowChoice,
	context: PromotionContext,
	outcomes: Record<number, PromotionOutcome>,
	canGraduate: boolean,
): RowStates {
	const done = succeededIds(outcomes);
	const next: RowStates = { ...rows };
	for (const row of flattenStudents(preview)) {
		const id = row.student.studentId;
		if (done.has(id) || !canBulkSet(choice, row, canGraduate)) continue;
		next[id] = withChoice(rows[id] ?? initialRowState(row.student, row.cls, context, canGraduate), choice, row.student, row.cls, context);
	}
	return next;
}

export interface ChoiceCounts {
	promote: number;
	repeat: number;
	graduate: number;
	skip: number;
	undecided: number;
	/** Already in the target year: locked, not counted in the choices above. */
	alreadyDone: number;
}

/** Live tally of the decisions still to be applied (students moved by an earlier apply are left out). */
export function countChoices(preview: PromotionPreviewClass[], rows: RowStates, outcomes: Record<number, PromotionOutcome> = {}): ChoiceCounts {
	const done = succeededIds(outcomes);
	const counts: ChoiceCounts = { promote: 0, repeat: 0, graduate: 0, skip: 0, undecided: 0, alreadyDone: 0 };
	for (const { student } of flattenStudents(preview)) {
		if (done.has(student.studentId)) continue;
		if (student.proposedAction === "ALREADY_DONE") {
			counts.alreadyDone++;
			continue;
		}
		const choice = rows[student.studentId]?.choice;
		if (choice === undefined) continue;
		if (choice === null) counts.undecided++;
		else if (choice === "PROMOTE") counts.promote++;
		else if (choice === "REPEAT") counts.repeat++;
		else if (choice === "GRADUATE") counts.graduate++;
		else counts.skip++;
	}
	return counts;
}

export interface ApplyReadiness {
	/** Rows with no choice yet (exceptions that could not default). */
	undecided: number;
	/** Non-skipped promote/repeat rows with no (valid) target section. */
	missingSection: number;
	/** Decisions that would be sent. */
	toApply: number;
	ready: boolean;
}

function hasValidSection(row: RowState, cls: PromotionPreviewClass, context: PromotionContext): boolean {
	return row.sectionId != null && sectionOptionsFor(row.choice, cls, context).some((o) => o.id === row.sectionId);
}

/** Students already moved by an earlier apply are left out; the rest must all be decided and placed. */
export function applyReadiness(
	preview: PromotionPreviewClass[],
	rows: RowStates,
	context: PromotionContext,
	outcomes: Record<number, PromotionOutcome> = {},
): ApplyReadiness {
	const done = succeededIds(outcomes);
	let undecided = 0;
	let missingSection = 0;
	let toApply = 0;
	for (const { cls, student } of flattenStudents(preview)) {
		if (done.has(student.studentId)) continue;
		const row = rows[student.studentId];
		if (!row) continue;
		if (row.choice === null) {
			undecided++;
			continue;
		}
		if (row.choice === "SKIP") continue;
		toApply++;
		if ((row.choice === "PROMOTE" || row.choice === "REPEAT") && !hasValidSection(row, cls, context)) missingSection++;
	}
	return { undecided, missingSection, toApply, ready: undecided === 0 && missingSection === 0 && toApply > 0 };
}

/** The apply request's decisions. `onlyIds` limits it (retry of the failed students); roll numbers are left to the server. */
export function buildDecisions(
	preview: PromotionPreviewClass[],
	rows: RowStates,
	outcomes: Record<number, PromotionOutcome> = {},
	onlyIds?: Set<number>,
): PromotionDecision[] {
	const done = succeededIds(outcomes);
	const decisions: PromotionDecision[] = [];
	for (const { student } of flattenStudents(preview)) {
		if (done.has(student.studentId)) continue;
		if (onlyIds && !onlyIds.has(student.studentId)) continue;
		const row = rows[student.studentId];
		if (!row || row.choice === null || row.choice === "SKIP") continue;
		decisions.push({
			studentId: student.studentId,
			action: row.choice,
			targetSectionId: row.choice === "GRADUATE" ? null : row.sectionId,
			rollNumber: null,
		});
	}
	return decisions;
}

export interface OutcomeSummary {
	/** Moved by this run. */
	succeeded: number;
	/** Already applied earlier - nothing was done, still fine. */
	skipped: number;
	failed: number;
}

export function summariseOutcomes(outcomes: PromotionOutcome[]): OutcomeSummary {
	return {
		succeeded: outcomes.filter((o) => o.succeeded && !o.skipped).length,
		skipped: outcomes.filter((o) => o.succeeded && o.skipped).length,
		failed: outcomes.filter((o) => !o.succeeded).length,
	};
}

/** A retry's results replace the earlier outcome of the same student. */
export function mergeOutcomes(prev: Record<number, PromotionOutcome>, next: PromotionOutcome[]): Record<number, PromotionOutcome> {
	return { ...prev, ...Object.fromEntries(next.map((o) => [o.studentId, o])) };
}
