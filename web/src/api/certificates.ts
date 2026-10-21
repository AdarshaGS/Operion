import { api, downloadFile } from "./client";

export type CertificateRequestStatus = "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | "ISSUED" | "CANCELLED";

export interface ManualField {
	key: string;
	label: string;
}

export interface CertificateTypeResponse {
	id: number;
	code: string;
	name: string;
	title: string;
	bodyTemplate: string;
	particularsTemplate: string | null;
	manualFields: string | null;
	manualFieldList: ManualField[];
	numberFormat: string;
	nextSequence: number;
	pageSize: string;
	oncePerStudent: boolean;
	requiresExit: boolean;
	requiresClearance: boolean;
	approvalRequired: boolean;
	guardianRequestable: boolean;
	active: boolean;
}

export interface UpdateCertificateTypeRequest {
	name: string;
	title: string;
	bodyTemplate: string;
	particularsTemplate: string | null;
	manualFields: string | null;
	numberFormat: string;
	pageSize: string;
	oncePerStudent: boolean;
	requiresExit: boolean;
	requiresClearance: boolean;
	approvalRequired: boolean;
	guardianRequestable: boolean;
	active: boolean;
}

export interface CertificateRequestResponse {
	id: number;
	studentId: number;
	studentName: string;
	admissionNumber: string | null;
	certificateTypeId: number;
	certificateTypeCode: string;
	certificateTypeName: string;
	approvalRequired: boolean;
	requiresClearance: boolean;
	manualFields: ManualField[];
	status: CertificateRequestStatus;
	purpose: string | null;
	manualValues: Record<string, string>;
	requestedVia: "STAFF" | "PORTAL";
	requestedBy: number | null;
	requestedAt: string;
	approvedBy: number | null;
	approvedAt: string | null;
	rejectionReason: string | null;
	clearanceOverrideReason: string | null;
	certificateNumber: string | null;
	issuedAt: string | null;
	printCount: number;
	cancelReason: string | null;
}

export interface ClearanceItem {
	code: string;
	label: string;
	clear: boolean;
	detail: string;
}

export interface Clearance {
	clear: boolean;
	items: ClearanceItem[];
}

export interface CertificateVerification {
	documentKind: string;
	certificateName: string;
	certificateNumber: string;
	issueDate: string;
	schoolName: string;
	studentName: string;
	status: "ISSUED" | "CANCELLED";
}

export const CERTIFICATE_STATUS_LABELS: Record<CertificateRequestStatus, string> = {
	PENDING_APPROVAL: "Awaiting approval",
	APPROVED: "Ready to issue",
	REJECTED: "Rejected",
	ISSUED: "Issued",
	CANCELLED: "Cancelled",
};

export function listCertificateTypes(): Promise<CertificateTypeResponse[]> {
	return api.get<CertificateTypeResponse[]>("/api/v1/certificate-types");
}

export function listCertificatePresets(): Promise<{ code: string; name: string }[]> {
	return api.get<{ code: string; name: string }[]>("/api/v1/certificate-types/presets");
}

export function getCertificateMergeFields(): Promise<Record<string, string>> {
	return api.get<Record<string, string>>("/api/v1/certificate-types/merge-fields");
}

export function createCertificateType(code: string, name: string): Promise<CertificateTypeResponse> {
	return api.post<CertificateTypeResponse>("/api/v1/certificate-types", { code, name });
}

export function updateCertificateType(id: number, request: UpdateCertificateTypeRequest): Promise<CertificateTypeResponse> {
	return api.put<CertificateTypeResponse>(`/api/v1/certificate-types/${id}`, request);
}

export function listCertificates(filter: { studentId?: number; status?: CertificateRequestStatus }): Promise<CertificateRequestResponse[]> {
	const params = new URLSearchParams();
	if (filter.studentId) params.set("studentId", String(filter.studentId));
	if (filter.status) params.set("status", filter.status);
	return api.get<CertificateRequestResponse[]>(`/api/v1/certificates?${params.toString()}`);
}

export function raiseCertificate(request: {
	studentId: number;
	certificateTypeId: number;
	purpose?: string | null;
	manualValues?: Record<string, string>;
}): Promise<CertificateRequestResponse> {
	return api.post<CertificateRequestResponse>("/api/v1/certificates", request);
}

export function updateCertificateDetails(
	id: number,
	request: { purpose?: string | null; manualValues?: Record<string, string> },
): Promise<CertificateRequestResponse> {
	return api.put<CertificateRequestResponse>(`/api/v1/certificates/${id}`, request);
}

export function approveCertificate(id: number): Promise<CertificateRequestResponse> {
	return api.post<CertificateRequestResponse>(`/api/v1/certificates/${id}/approve`);
}

export function rejectCertificate(id: number, reason: string): Promise<CertificateRequestResponse> {
	return api.post<CertificateRequestResponse>(`/api/v1/certificates/${id}/reject`, { reason });
}

export function getCertificateClearance(id: number): Promise<Clearance> {
	return api.get<Clearance>(`/api/v1/certificates/${id}/clearance`);
}

/** reason is the dues-clearance override, only needed when something is outstanding. */
export function issueCertificate(id: number, reason?: string | null): Promise<CertificateRequestResponse> {
	return api.post<CertificateRequestResponse>(`/api/v1/certificates/${id}/issue`, { reason: reason ?? null });
}

export function cancelCertificate(id: number, reason: string): Promise<CertificateRequestResponse> {
	return api.post<CertificateRequestResponse>(`/api/v1/certificates/${id}/cancel`, { reason });
}

export function downloadCertificate(id: number, certificateNumber: string | null): Promise<void> {
	return downloadFile(`/api/v1/certificates/${id}/certificate.pdf`, `${certificateNumber ?? "certificate"}.pdf`);
}

/** Public, unauthenticated - the page a certificate's QR code opens. */
export function verifyCertificate(slug: string, token: string): Promise<CertificateVerification> {
	return api.get<CertificateVerification>(`/api/v1/public/orgs/${encodeURIComponent(slug)}/certificates/${encodeURIComponent(token)}`);
}
