import { api } from "./client";

export interface CreateSubjectRequest {
	name: string;
	code: string;
	elective: boolean;
}

export interface SubjectResponse {
	id: number;
	name: string;
	code: string;
	elective: boolean;
	status: string;
}

export function createSubject(request: CreateSubjectRequest): Promise<SubjectResponse> {
	return api.post<SubjectResponse>("/api/v1/subjects", request);
}

export function listSubjects(): Promise<SubjectResponse[]> {
	return api.get<SubjectResponse[]>("/api/v1/subjects");
}

export function changeSubjectStatus(id: number, status: string): Promise<SubjectResponse> {
	return api.post<SubjectResponse>(`/api/v1/subjects/${id}/status`, { status });
}
