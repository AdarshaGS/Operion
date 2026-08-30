import { api } from "./client";

export interface AssignSubjectRequest {
	subjectId: number;
	mandatory: boolean;
}

export interface ClassSubjectResponse {
	id: number;
	schoolClassId: number;
	subjectId: number;
	mandatory: boolean;
	status: string;
}

export function assignSubjectToClass(classId: number, request: AssignSubjectRequest): Promise<ClassSubjectResponse> {
	return api.post<ClassSubjectResponse>(`/api/v1/school-classes/${classId}/subjects`, request);
}

export function listClassSubjects(classId: number): Promise<ClassSubjectResponse[]> {
	return api.get<ClassSubjectResponse[]>(`/api/v1/school-classes/${classId}/subjects`);
}

export function changeClassSubjectStatus(classId: number, id: number, status: string): Promise<ClassSubjectResponse> {
	return api.post<ClassSubjectResponse>(`/api/v1/school-classes/${classId}/subjects/${id}/status`, { status });
}

export function updateClassSubject(classId: number, id: number, mandatory: boolean): Promise<ClassSubjectResponse> {
	return api.patch<ClassSubjectResponse>(`/api/v1/school-classes/${classId}/subjects/${id}`, { mandatory });
}
