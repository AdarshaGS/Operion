import { api } from "./client";

export interface CreateGradeLevelRequest {
	name: string;
	sequenceOrder: number;
	stage?: string | null;
}

export interface GradeLevelResponse {
	id: number;
	name: string;
	sequenceOrder: number;
	stage: string | null;
	status: string;
}

export function createGradeLevel(request: CreateGradeLevelRequest): Promise<GradeLevelResponse> {
	return api.post<GradeLevelResponse>("/api/v1/grade-levels", request);
}

export function listGradeLevels(): Promise<GradeLevelResponse[]> {
	return api.get<GradeLevelResponse[]>("/api/v1/grade-levels");
}

export function changeGradeLevelStatus(id: number, status: string): Promise<GradeLevelResponse> {
	return api.post<GradeLevelResponse>(`/api/v1/grade-levels/${id}/status`, { status });
}

export function updateGradeLevel(id: number, request: CreateGradeLevelRequest): Promise<GradeLevelResponse> {
	return api.patch<GradeLevelResponse>(`/api/v1/grade-levels/${id}`, request);
}
