import { platformApi } from "./platformClient";

export interface PlanResponse {
	id: number;
	code: string;
	name: string;
	pricePerStudentPerYear: number;
	status: string;
}

export interface CreatePlanRequest {
	code: string;
	name: string;
	pricePerStudentPerYear: number;
}

export function listPlans(): Promise<PlanResponse[]> {
	return platformApi.get<PlanResponse[]>("/api/v1/platform/plans");
}

export function createPlan(request: CreatePlanRequest): Promise<PlanResponse> {
	return platformApi.post<PlanResponse>("/api/v1/platform/plans", request);
}

export function changePlanStatus(id: number, status: string): Promise<PlanResponse> {
	return platformApi.post<PlanResponse>(`/api/v1/platform/plans/${id}/status`, { status });
}
