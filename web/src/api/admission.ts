import { api } from "./client";

/** OFF = nothing; TRACK = fees assigned/invoiced at enrolment and the status shown; BLOCK = TRACK plus (not enforced yet) blocking marks/academic records while the admission fee is pending. */
export type AdmissionFeeClearanceMode = "OFF" | "TRACK" | "BLOCK";

export interface AdmissionSettingsResponse {
	feeClearanceMode: AdmissionFeeClearanceMode;
}

export interface UpdateAdmissionSettingsRequest {
	feeClearanceMode: AdmissionFeeClearanceMode;
}

export function getAdmissionSettings(): Promise<AdmissionSettingsResponse> {
	return api.get<AdmissionSettingsResponse>("/api/v1/admission-settings");
}

export function updateAdmissionSettings(request: UpdateAdmissionSettingsRequest): Promise<AdmissionSettingsResponse> {
	return api.put<AdmissionSettingsResponse>("/api/v1/admission-settings", request);
}

export type FeeClearanceStatus = "NOT_REQUIRED" | "PENDING" | "CLEARED" | "EXEMPTED";

export interface AdmissionFeePreviewLine {
	feeStructureId: number;
	categoryName: string;
	amount: number;
	firstInstallment: { number: number; dueDate: string; amount: number };
}

/** What enrolling into this year and section would charge up front. `structures` is empty while the workflow is inactive (mode OFF or FEES off). */
export interface AdmissionFeePreviewResponse {
	mode: AdmissionFeeClearanceMode;
	structures: AdmissionFeePreviewLine[];
	admissionTotal: number;
}

export function getAdmissionFeePreview(academicYearId: number, sectionId: number): Promise<AdmissionFeePreviewResponse> {
	return api.get<AdmissionFeePreviewResponse>(
		`/api/v1/admission-fees/preview?academicYearId=${academicYearId}&sectionId=${sectionId}`,
	);
}

export type FeeClearanceStepKey = "ENROLLED" | "FEES_ASSIGNED" | "INVOICES_CREATED" | "FIRST_PAYMENT";

export interface FeeClearanceStep {
	key: FeeClearanceStepKey;
	done: boolean;
	/** ISO instant; null while the step is not done. */
	at: string | null;
}

export interface FeeClearanceInvoiceLine {
	id: number;
	number: string;
	total: number;
	paid: number;
	outstanding: number;
	dueDate: string;
}

export interface FeeClearanceOverrideInfo {
	by: number;
	at: string;
	reason: string;
}

export interface FeeClearanceViewResponse {
	status: FeeClearanceStatus;
	mode: AdmissionFeeClearanceMode;
	steps: FeeClearanceStep[];
	invoices: FeeClearanceInvoiceLine[];
	override: FeeClearanceOverrideInfo | null;
}

export function getFeeClearance(studentId: number, enrollmentId: number): Promise<FeeClearanceViewResponse> {
	return api.get<FeeClearanceViewResponse>(`/api/v1/students/${studentId}/enrollments/${enrollmentId}/fee-clearance`);
}
