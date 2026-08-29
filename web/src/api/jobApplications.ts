import { api } from "./client";

export interface SubmitJobApplicationRequest {
	organisationSlug: string;
	applicantName: string;
	email: string;
	specialization?: string | null;
	yearsExperience?: number | null;
}

export interface JobApplicationResponse {
	id: number;
	applicantName: string;
	email: string;
	specialization: string | null;
	yearsExperience: number | null;
	status: string;
	appliedAt: string;
	decidedBy: number | null;
	decidedAt: string | null;
}

/** Public, unauthenticated - same trust tier as claimInvite. Reached from the /careers form. */
export function submitJobApplication(request: SubmitJobApplicationRequest): Promise<JobApplicationResponse> {
	return api.post<JobApplicationResponse>("/api/v1/job-applications", request);
}

export function listJobApplications(status?: string): Promise<JobApplicationResponse[]> {
	const params = new URLSearchParams();
	if (status) params.set("status", status);
	return api.get<JobApplicationResponse[]>(`/api/v1/job-applications?${params.toString()}`);
}

export function approveJobApplication(id: number): Promise<JobApplicationResponse> {
	return api.post<JobApplicationResponse>(`/api/v1/job-applications/${id}/approve`);
}

export function rejectJobApplication(id: number): Promise<JobApplicationResponse> {
	return api.post<JobApplicationResponse>(`/api/v1/job-applications/${id}/reject`);
}
