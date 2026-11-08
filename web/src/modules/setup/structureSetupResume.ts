export type StepKey = "profile" | "business" | "campuses";

export interface ResumeStep {
	key: StepKey;
	/** True when the step has a data requirement (profile, business) that must be satisfied before moving on. */
	required: boolean;
}

export interface ResumeDecision {
	/** Steps to tick in the stepper. */
	completed: Set<StepKey>;
	/** Index of the step to open on; steps.length is the Review step. */
	openIndex: number;
}

/**
 * Where a returning user should land in the Structure Setup wizard.
 *
 * - A required step counts as done only while its data requirement is satisfied - a saved "completed"
 *   flag can't excuse a step whose data has since gone missing, so required steps can't be skipped.
 * - An optional step (no requirement) counts as done only once the user continued past it (saved on the server).
 * - The wizard opens on the first step that is not done, or on Review when every step is.
 *
 * @param satisfied keys of the required steps whose requirement the saved data currently meets
 * @param savedOnServer step keys the server has recorded as completed (unknown keys are ignored)
 */
export function decideResume(steps: ResumeStep[], satisfied: ReadonlySet<StepKey>, savedOnServer: readonly string[]): ResumeDecision {
	const saved = new Set(savedOnServer);
	const completed = new Set<StepKey>();
	for (const step of steps) {
		const done = step.required ? satisfied.has(step.key) : saved.has(step.key);
		if (done) completed.add(step.key);
	}
	const firstIncomplete = steps.findIndex((step) => !completed.has(step.key));
	return { completed, openIndex: firstIncomplete === -1 ? steps.length : firstIncomplete };
}
