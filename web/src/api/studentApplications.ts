import { api } from "./client";

export interface SubmitStudentApplicationRequest {
	applicantName: string;
	dateOfBirth?: string | null;
	gender?: string | null;
	guardianName?: string | null;
	guardianPhone?: string | null;
	desiredGradeLevelId?: number | null;
	notes?: string | null;
}

export interface StudentApplicationResponse {
	id: number;
	applicantName: string;
	dateOfBirth: string | null;
	gender: string | null;
	guardianName: string | null;
	guardianPhone: string | null;
	desiredGradeLevelId: number | null;
	notes: string | null;
	status: string;
	appliedAt: string;
	decidedBy: number | null;
	decidedAt: string | null;
}

export function submitStudentApplication(request: SubmitStudentApplicationRequest): Promise<StudentApplicationResponse> {
	return api.post<StudentApplicationResponse>("/api/v1/student-applications", request);
}

export function listStudentApplications(status?: string): Promise<StudentApplicationResponse[]> {
	const params = status ? `?status=${status}` : "";
	return api.get<StudentApplicationResponse[]>(`/api/v1/student-applications${params}`);
}

export function approveStudentApplication(id: number): Promise<StudentApplicationResponse> {
	return api.post<StudentApplicationResponse>(`/api/v1/student-applications/${id}/approve`);
}

export function rejectStudentApplication(id: number): Promise<StudentApplicationResponse> {
	return api.post<StudentApplicationResponse>(`/api/v1/student-applications/${id}/reject`);
}
