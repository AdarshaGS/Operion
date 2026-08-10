import { api } from "./client";

export interface CreateSchoolClassRequest {
	academicYearId: number;
	campusId: number;
	gradeLevelId: number;
	displayName?: string | null;
}

export interface SchoolClassResponse {
	id: number;
	academicYearId: number;
	campusId: number;
	gradeLevelId: number;
	displayName: string | null;
	status: string;
}

export function createSchoolClass(request: CreateSchoolClassRequest): Promise<SchoolClassResponse> {
	return api.post<SchoolClassResponse>("/api/v1/school-classes", request);
}

export function listSchoolClasses(academicYearId?: number): Promise<SchoolClassResponse[]> {
	const query = academicYearId ? `?academicYearId=${academicYearId}` : "";
	return api.get<SchoolClassResponse[]>(`/api/v1/school-classes${query}`);
}

export function changeSchoolClassStatus(id: number, status: string): Promise<SchoolClassResponse> {
	return api.post<SchoolClassResponse>(`/api/v1/school-classes/${id}/status`, { status });
}
