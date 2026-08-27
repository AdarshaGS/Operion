import { api } from "./client";

export interface CreateDepartmentRequest {
	name: string;
}

export interface DepartmentResponse {
	id: number;
	name: string;
	status: string;
}

export function createDepartment(request: CreateDepartmentRequest): Promise<DepartmentResponse> {
	return api.post<DepartmentResponse>("/api/v1/departments", request);
}

export function listDepartments(): Promise<DepartmentResponse[]> {
	return api.get<DepartmentResponse[]>("/api/v1/departments");
}

export function changeDepartmentStatus(id: number, status: string): Promise<DepartmentResponse> {
	return api.post<DepartmentResponse>(`/api/v1/departments/${id}/status`, { status });
}
