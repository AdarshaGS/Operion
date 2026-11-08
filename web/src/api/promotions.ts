import { api } from "./client";

export type PromotionAction = "PROMOTE" | "REPEAT" | "GRADUATE";

export interface PromotionDecision {
	studentId: number;
	action: PromotionAction;
	targetSectionId: number | null;
	rollNumber: number | null;
}

export interface PromotionOutcome {
	studentId: number;
	action: PromotionAction;
	succeeded: boolean;
	/** True when the decision was already applied, so nothing was done (still succeeded). */
	skipped: boolean;
	error: string | null;
}

export interface PromotionRequest {
	targetAcademicYearId: number | null;
	promotionDate: string;
	decisions: PromotionDecision[];
}

/** Year-end promotion for many students at once; each decision succeeds or fails on its own. */
export function applyPromotions(request: PromotionRequest): Promise<PromotionOutcome[]> {
	return api.post<PromotionOutcome[]>("/api/v1/student-promotions", request);
}

export type PromotionProposal = "PROMOTE" | "REPEAT" | "GRADUATE" | "NEEDS_DECISION" | "ALREADY_DONE";

export interface ClassMapping {
	sourceSchoolClassId: number;
	/** null = graduate the whole class. */
	targetSchoolClassId: number | null;
}

export interface PromotionPreviewRequest {
	sourceAcademicYearId: number;
	targetAcademicYearId: number;
	effectiveDate: string;
	/** Optional overrides of the default next-grade mapping; classes left out use the default. */
	classMap?: ClassMapping[];
}

export interface PromotionPreviewTargetSection {
	id: number;
	name: string;
	/** null when the section has no capacity limit. */
	remainingCapacity: number | null;
}

export interface PromotionPreviewStudent {
	studentId: number;
	name: string;
	admissionNumber: string;
	currentSectionId: number;
	proposedAction: PromotionProposal;
	reason: string;
	suggestedTargetSectionId: number | null;
}

export interface PromotionPreviewClass {
	sourceSchoolClassId: number;
	label: string;
	/** null when graduating, or when the next grade has no class in the target year (see reason). */
	proposedTargetSchoolClassId: number | null;
	proposedAction: "PROMOTE" | "GRADUATE";
	reason: string | null;
	targetSections: PromotionPreviewTargetSection[];
	students: PromotionPreviewStudent[];
}

/** Read-only suggestions for year-end promotion - nothing is written; submit explicit decisions via applyPromotions. */
export function previewPromotion(request: PromotionPreviewRequest): Promise<PromotionPreviewClass[]> {
	return api.post<PromotionPreviewClass[]>("/api/v1/student-promotions/preview", request);
}
