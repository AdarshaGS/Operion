import { api, type PageResponse } from "./client";

/** Row of the Admissions review queue. Person-name fields are all optional because the
 * backend contract names them loosely - see admissionApplicantName(). */
export interface AdmissionApplicantResponse {
	id: number;
	personId?: number;
	personName?: string | null;
	firstName?: string | null;
	lastName?: string | null;
	dateOfBirth: string | null;
	desiredGrade: { id: number; name: string } | null;
	guardianName: string | null;
	guardianPhone: string | null;
	guardianEmail: string | null;
	status: string;
	inquiryDate: string;
	rejectionReason: string | null;
}

export type AdmissionStatusCounts = Record<"ALL" | "INQUIRY" | "UNDER_REVIEW" | "CONVERTED" | "REJECTED", number>;

export interface AdmissionDocument {
	docType: string;
	received: boolean;
}

export function admissionApplicantName(a: AdmissionApplicantResponse): string {
	return a.personName || [a.firstName, a.lastName].filter(Boolean).join(" ") || `Applicant #${a.id}`;
}

export function listAdmissionApplicants(status: string | null, page: number, size: number): Promise<PageResponse<AdmissionApplicantResponse>> {
	const params = new URLSearchParams({ page: String(page), size: String(size) });
	if (status && status !== "ALL") params.set("status", status);
	return api.get<PageResponse<AdmissionApplicantResponse>>(`/api/v1/applicants?${params}`);
}

export function getAdmissionStatusCounts(): Promise<AdmissionStatusCounts> {
	return api.get<AdmissionStatusCounts>("/api/v1/applicants/status-counts");
}

export function startApplicantReview(id: number): Promise<unknown> {
	return api.post(`/api/v1/applicants/${id}/start-review`);
}

export function rejectApplicantWithReason(id: number, reason: string): Promise<unknown> {
	return api.post(`/api/v1/applicants/${id}/reject`, { reason });
}

export function getApplicantDocuments(id: number): Promise<AdmissionDocument[]> {
	return api.get<AdmissionDocument[]>(`/api/v1/applicants/${id}/documents`);
}

export function saveApplicantDocuments(id: number, docs: AdmissionDocument[]): Promise<AdmissionDocument[]> {
	return api.put<AdmissionDocument[]>(`/api/v1/applicants/${id}/documents`, docs);
}
