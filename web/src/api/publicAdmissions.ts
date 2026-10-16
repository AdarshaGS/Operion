import { api } from "./client";

export interface PublicGradeOption {
	id: number;
	name: string;
}

export interface AdmissionFormOptions {
	organisationName: string;
	grades: PublicGradeOption[];
}

export interface SubmitAdmissionInquiryRequest {
	firstName: string;
	lastName: string;
	dateOfBirth: string;
	gender: string;
	desiredGradeId?: number | null;
	guardianName: string;
	guardianPhone: string;
	guardianEmail?: string | null;
	notes?: string | null;
	/** Honeypot - always sent empty by the real form; bots that fill it are dropped server-side. */
	website?: string;
}

const base = (slug: string) => `/api/v1/public/orgs/${encodeURIComponent(slug)}/admission-inquiries`;

/** Public, unauthenticated. */
export function getAdmissionFormOptions(slug: string): Promise<AdmissionFormOptions> {
	return api.get<AdmissionFormOptions>(`${base(slug)}/form-options`);
}

/** Public, unauthenticated. 429 = rate limited. */
export function submitAdmissionInquiry(slug: string, request: SubmitAdmissionInquiryRequest): Promise<{ id: number }> {
	return api.post<{ id: number }>(base(slug), request);
}
