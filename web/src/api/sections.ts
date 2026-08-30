import { api } from "./client";

export interface CreateSectionRequest {
	name: string;
	capacity?: number | null;
	room?: string | null;
}

export interface SectionResponse {
	id: number;
	schoolClassId: number;
	name: string;
	capacity: number | null;
	room: string | null;
	status: string;
}

export function createSection(classId: number, request: CreateSectionRequest): Promise<SectionResponse> {
	return api.post<SectionResponse>(`/api/v1/school-classes/${classId}/sections`, request);
}

export function listSections(classId: number): Promise<SectionResponse[]> {
	return api.get<SectionResponse[]>(`/api/v1/school-classes/${classId}/sections`);
}

export function changeSectionStatus(classId: number, sectionId: number, status: string): Promise<SectionResponse> {
	return api.post<SectionResponse>(`/api/v1/school-classes/${classId}/sections/${sectionId}/status`, { status });
}

export function updateSection(classId: number, sectionId: number, request: CreateSectionRequest): Promise<SectionResponse> {
	return api.patch<SectionResponse>(`/api/v1/school-classes/${classId}/sections/${sectionId}`, request);
}
