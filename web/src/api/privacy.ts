import { api } from "./client";

export interface ConsentPurposeResponse {
	id: number;
	code: string;
	name: string;
	description: string | null;
	status: "ACTIVE" | "INACTIVE";
}

export type ConsentSource = "ADMIN_ENTRY" | "PAPER_FORM" | "VERBAL";
export type ConsentState = "GRANTED" | "WITHDRAWN" | "NOT_RECORDED";

export interface CurrentConsentResponse {
	purposeId: number;
	purposeCode: string;
	purposeName: string;
	state: ConsentState;
	since: string | null;
	source: ConsentSource | null;
}

export interface ConsentRecordResponse {
	id: number;
	personId: number;
	purposeId: number;
	purposeName: string;
	granted: boolean;
	source: ConsentSource;
	note: string | null;
	recordedAt: string;
}

export type DataSubjectRequestType = "ACCESS" | "EXPORT" | "CORRECTION" | "DELETION";
export type DataSubjectRequestStatus = "RECEIVED" | "IDENTITY_VERIFIED" | "COMPLETED" | "REJECTED";

export interface DataSubjectRequestResponse {
	id: number;
	personId: number;
	personName: string;
	requestType: DataSubjectRequestType;
	status: DataSubjectRequestStatus;
	description: string | null;
	receivedDate: string;
	dueDate: string;
	overdue: boolean;
	identityVerifiedAt: string | null;
	identityVerificationMethod: string | null;
	resolvedAt: string | null;
	resolutionNote: string | null;
}

export function listConsentPurposes(): Promise<ConsentPurposeResponse[]> {
	return api.get<ConsentPurposeResponse[]>("/api/v1/consent-purposes");
}

export function createConsentPurpose(request: { code: string; name: string; description?: string | null }): Promise<ConsentPurposeResponse> {
	return api.post<ConsentPurposeResponse>("/api/v1/consent-purposes", request);
}

export function changeConsentPurposeStatus(id: number, status: "ACTIVE" | "INACTIVE"): Promise<ConsentPurposeResponse> {
	return api.post<ConsentPurposeResponse>(`/api/v1/consent-purposes/${id}/status`, { status });
}

export function getCurrentConsents(personId: number): Promise<CurrentConsentResponse[]> {
	return api.get<CurrentConsentResponse[]>(`/api/v1/consents/current?personId=${personId}`);
}

export function getConsentHistory(personId: number): Promise<ConsentRecordResponse[]> {
	return api.get<ConsentRecordResponse[]>(`/api/v1/consents/history?personId=${personId}`);
}

export function recordConsent(request: {
	personId: number;
	purposeId: number;
	granted: boolean;
	source: ConsentSource;
	note?: string | null;
}): Promise<ConsentRecordResponse> {
	return api.post<ConsentRecordResponse>("/api/v1/consents", request);
}

export function listDataSubjectRequests(status?: DataSubjectRequestStatus): Promise<DataSubjectRequestResponse[]> {
	return api.get<DataSubjectRequestResponse[]>(`/api/v1/data-subject-requests${status ? `?status=${status}` : ""}`);
}

export function openDataSubjectRequest(request: {
	personId: number;
	requestType: DataSubjectRequestType;
	description?: string | null;
	receivedDate?: string | null;
	dueDate?: string | null;
}): Promise<DataSubjectRequestResponse> {
	return api.post<DataSubjectRequestResponse>("/api/v1/data-subject-requests", request);
}

export function verifyDataSubjectIdentity(id: number, method: string): Promise<DataSubjectRequestResponse> {
	return api.post<DataSubjectRequestResponse>(`/api/v1/data-subject-requests/${id}/verify-identity`, { method });
}

export function completeDataSubjectRequest(id: number, resolutionNote: string): Promise<DataSubjectRequestResponse> {
	return api.post<DataSubjectRequestResponse>(`/api/v1/data-subject-requests/${id}/complete`, { resolutionNote });
}

export function rejectDataSubjectRequest(id: number, resolutionNote: string): Promise<DataSubjectRequestResponse> {
	return api.post<DataSubjectRequestResponse>(`/api/v1/data-subject-requests/${id}/reject`, { resolutionNote });
}

export function getDataSubjectExport(id: number): Promise<Record<string, unknown>> {
	return api.get<Record<string, unknown>>(`/api/v1/data-subject-requests/${id}/data-export`);
}
