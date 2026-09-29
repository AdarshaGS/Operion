import { api, type PageResponse } from "./client";
import type { CreateStudentRequest, StudentResponse } from "./students";

export interface CreateApplicantRequest {
	personId: number;
	inquiryDate: string;
	source?: string | null;
	notes?: string | null;
}

export interface ApplicantResponse {
	id: number;
	personId: number;
	inquiryDate: string;
	source: string | null;
	notes: string | null;
	status: string;
}

/** Same admission fields as CreateStudentRequest, minus personId - convert() already
 * knows the applicant's own Person. */
export type ConvertApplicantRequest = Omit<CreateStudentRequest, "personId">;

export function inquireApplicant(request: CreateApplicantRequest): Promise<ApplicantResponse> {
	return api.post<ApplicantResponse>("/api/v1/applicants", request);
}

export function listApplicants(): Promise<ApplicantResponse[]> {
	// The list endpoint is paged now (#219); this legacy caller only wants the first page of INQUIRY rows.
	return api.get<PageResponse<ApplicantResponse>>("/api/v1/applicants?status=INQUIRY&size=100").then((page) => page.content);
}

export function rejectApplicant(id: number): Promise<ApplicantResponse> {
	return api.post<ApplicantResponse>(`/api/v1/applicants/${id}/reject`);
}

export function convertApplicant(id: number, request: ConvertApplicantRequest): Promise<StudentResponse> {
	return api.post<StudentResponse>(`/api/v1/applicants/${id}/convert`, request);
}
