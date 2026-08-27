import { api } from "./client";

export interface CreateStaffProfileRequest {
	personId: number;
	campusId?: number | null;
	employeeCode: string;
	designationId: number;
	departmentId?: number | null;
	dateOfJoining: string;
	employmentType: string;
}

export interface StaffProfileResponse {
	id: number;
	personId: number;
	campusId: number | null;
	employeeCode: string;
	designationId: number;
	designationName: string;
	departmentId: number | null;
	departmentName: string | null;
	dateOfJoining: string;
	employmentType: string;
	status: string;
}

export interface ChangeStaffStatusRequest {
	status: string;
}

export interface AddStaffDocumentRequest {
	documentType: string;
	fileReference: string;
	fileName: string;
	mimeType?: string | null;
}

export interface StaffDocumentResponse {
	id: number;
	staffProfileId: number;
	documentType: string;
	fileReference: string;
	fileName: string;
	mimeType: string | null;
	verificationStatus: string;
	verifiedBy: number | null;
	verifiedAt: string | null;
	status: string;
}

export interface VerifyStaffDocumentRequest {
	verificationStatus: string;
	verifiedBy: number;
}

export function createStaffProfile(request: CreateStaffProfileRequest): Promise<StaffProfileResponse> {
	return api.post<StaffProfileResponse>("/api/v1/hr/staff", request);
}

export function listStaffProfiles(campusId?: number): Promise<StaffProfileResponse[]> {
	const query = campusId ? `?campusId=${campusId}` : "";
	return api.get<StaffProfileResponse[]>(`/api/v1/hr/staff${query}`);
}

export function changeStaffStatus(staffProfileId: number, request: ChangeStaffStatusRequest): Promise<StaffProfileResponse> {
	return api.post<StaffProfileResponse>(`/api/v1/hr/staff/${staffProfileId}/status`, request);
}

export function addStaffDocument(staffProfileId: number, request: AddStaffDocumentRequest): Promise<StaffDocumentResponse> {
	return api.post<StaffDocumentResponse>(`/api/v1/hr/staff/${staffProfileId}/documents`, request);
}

export function listStaffDocuments(staffProfileId: number): Promise<StaffDocumentResponse[]> {
	return api.get<StaffDocumentResponse[]>(`/api/v1/hr/staff/${staffProfileId}/documents`);
}

export function verifyStaffDocument(documentId: number, request: VerifyStaffDocumentRequest): Promise<StaffDocumentResponse> {
	return api.post<StaffDocumentResponse>(`/api/v1/hr/staff/documents/${documentId}/verify`, request);
}
