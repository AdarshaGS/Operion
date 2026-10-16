import { api } from "./client";

export interface CreateAcademicYearRequest {
	name: string;
	startDate: string;
	endDate: string;
}

export interface AcademicYearResponse {
	id: number;
	name: string;
	startDate: string;
	endDate: string;
	current: boolean;
	status: string;
}

export function createAcademicYear(request: CreateAcademicYearRequest): Promise<AcademicYearResponse> {
	return api.post<AcademicYearResponse>("/api/v1/academic-years", request);
}

export function listAcademicYears(): Promise<AcademicYearResponse[]> {
	return api.get<AcademicYearResponse[]>("/api/v1/academic-years");
}

export function markAcademicYearCurrent(id: number): Promise<AcademicYearResponse> {
	return api.post<AcademicYearResponse>(`/api/v1/academic-years/${id}/mark-current`);
}

export function closeAcademicYear(id: number): Promise<AcademicYearResponse> {
	return api.post<AcademicYearResponse>(`/api/v1/academic-years/${id}/close`);
}

export interface RolloverStepOption {
	key: string;
	label: string;
	module: string;
}

export interface RolloverItem {
	name: string;
	copied: number;
	alreadyThere: number;
}

export interface RolloverStepResult {
	key: string;
	label: string;
	items: RolloverItem[];
	notes: string[];
}

export interface RolloverReport {
	dryRun: boolean;
	sourceYear: string;
	targetYear: string;
	steps: RolloverStepResult[];
	/** Step labels left out because their module is switched off for this school. */
	unavailable: string[];
}

export interface RolloverRequest {
	sourceYearId: number;
	/** Empty means every available step. */
	steps: string[];
	feeIncreasePercent: number | null;
}

export function listRolloverSteps(): Promise<RolloverStepOption[]> {
	return api.get<RolloverStepOption[]>("/api/v1/academic-years/rollover/steps");
}

/** Runs the real logic and rolls back: the counts are exactly what apply would do. */
export function previewRollover(targetYearId: number, request: RolloverRequest): Promise<RolloverReport> {
	return api.post<RolloverReport>(`/api/v1/academic-years/${targetYearId}/rollover/preview`, request);
}

export function applyRollover(targetYearId: number, request: RolloverRequest): Promise<RolloverReport> {
	return api.post<RolloverReport>(`/api/v1/academic-years/${targetYearId}/rollover/apply`, request);
}
