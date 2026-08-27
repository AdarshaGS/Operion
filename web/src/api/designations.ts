import { api } from "./client";

export interface CreateDesignationRequest {
	name: string;
}

export interface DesignationResponse {
	id: number;
	name: string;
	status: string;
}

export function createDesignation(request: CreateDesignationRequest): Promise<DesignationResponse> {
	return api.post<DesignationResponse>("/api/v1/designations", request);
}

export function listDesignations(): Promise<DesignationResponse[]> {
	return api.get<DesignationResponse[]>("/api/v1/designations");
}

export function changeDesignationStatus(id: number, status: string): Promise<DesignationResponse> {
	return api.post<DesignationResponse>(`/api/v1/designations/${id}/status`, { status });
}
