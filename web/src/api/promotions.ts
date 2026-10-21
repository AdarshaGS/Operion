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
