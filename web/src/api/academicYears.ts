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
