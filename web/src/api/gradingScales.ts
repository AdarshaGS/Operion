import { api } from "./client";

export interface GradingBandEntry {
	grade: string;
	minPercentage: number;
	remark?: string | null;
}

export interface CreateGradingScaleRequest {
	name: string;
	defaultScale: boolean;
	bands: GradingBandEntry[];
}

export interface GradingScaleResponse {
	id: number;
	name: string;
	defaultScale: boolean;
	bands: GradingBandEntry[];
}

export function createGradingScale(request: CreateGradingScaleRequest): Promise<GradingScaleResponse> {
	return api.post<GradingScaleResponse>("/api/v1/examinations/grading-scales", request);
}

export function listGradingScales(): Promise<GradingScaleResponse[]> {
	return api.get<GradingScaleResponse[]>("/api/v1/examinations/grading-scales");
}
