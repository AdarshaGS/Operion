import { api } from "./client";

export interface CreateStaffProfileRequest {
	personId: number;
	campusId?: number | null;
	employeeCode: string;
	designationId: number;
	departmentId?: number | null;
	dateOfJoining: string;
	employmentType: string;
	reportingManagerId?: number | null;
}

export interface StaffProfileResponse {
	id: number;
	personId: number;
	address: string | null;
	campusId: number | null;
	employeeCode: string;
	designationId: number;
	designationName: string;
	departmentId: number | null;
	departmentName: string | null;
	reportingManagerId: number | null;
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
	expiryDate?: string | null;
}

export interface StaffDocumentResponse {
	id: number;
	staffProfileId: number;
	documentType: string;
	fileReference: string;
	fileName: string;
	mimeType: string | null;
	expiryDate: string | null;
	verificationStatus: string;
	verifiedBy: number | null;
	verifiedAt: string | null;
	status: string;
}

export interface VerifyStaffDocumentRequest {
	verificationStatus: string;
	verifiedBy: number;
}

export interface TransferStaffRequest {
	campusId?: number | null;
	departmentId?: number | null;
	designationId: number;
	effectiveDate: string;
}

export interface StaffAssignmentResponse {
	id: number;
	staffProfileId: number;
	campusId: number | null;
	departmentId: number | null;
	designationId: number;
	designationName: string;
	startDate: string;
	endDate: string | null;
	status: string;
}

export interface RecordStaffExitRequest {
	exitType: string;
	exitDate: string;
	reason?: string | null;
	initiatedBy?: number | null;
}

export interface StaffExitResponse {
	id: number;
	staffProfileId: number;
	exitType: string;
	exitDate: string;
	reason: string | null;
	initiatedBy: number | null;
}

export interface UpsertStaffBankDetailsRequest {
	bankAccountHolderName?: string | null;
	bankAccountNumber?: string | null;
	bankName?: string | null;
	bankBranchCode?: string | null;
	taxIdentifier?: string | null;
}

export interface StaffBankDetailResponse {
	id: number;
	staffProfileId: number;
	bankAccountHolderName: string | null;
	bankAccountNumber: string | null;
	bankName: string | null;
	bankBranchCode: string | null;
	taxIdentifier: string | null;
}

export function createStaffProfile(request: CreateStaffProfileRequest): Promise<StaffProfileResponse> {
	return api.post<StaffProfileResponse>("/api/v1/hr/staff", request);
}

export function listStaffProfiles(campusId?: number): Promise<StaffProfileResponse[]> {
	const query = campusId ? `?campusId=${campusId}` : "";
	return api.get<StaffProfileResponse[]>(`/api/v1/hr/staff${query}`);
}

/** Unlike listStaffProfiles(), this resolves regardless of status - the only way to look
 * up a specific staff member once they're RESIGNED/TERMINATED. */
export function getStaffProfile(staffProfileId: number): Promise<StaffProfileResponse> {
	return api.get<StaffProfileResponse>(`/api/v1/hr/staff/${staffProfileId}`);
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

export function transferStaff(staffProfileId: number, request: TransferStaffRequest): Promise<StaffProfileResponse> {
	return api.post<StaffProfileResponse>(`/api/v1/hr/staff/${staffProfileId}/transfer`, request);
}

export function listStaffAssignments(staffProfileId: number): Promise<StaffAssignmentResponse[]> {
	return api.get<StaffAssignmentResponse[]>(`/api/v1/hr/staff/${staffProfileId}/assignments`);
}

export function recordStaffExit(staffProfileId: number, request: RecordStaffExitRequest): Promise<StaffExitResponse> {
	return api.post<StaffExitResponse>(`/api/v1/hr/staff/${staffProfileId}/exit`, request);
}

export function listStaffExits(staffProfileId: number): Promise<StaffExitResponse[]> {
	return api.get<StaffExitResponse[]>(`/api/v1/hr/staff/${staffProfileId}/exits`);
}

export function getStaffBankDetails(staffProfileId: number): Promise<StaffBankDetailResponse | null> {
	return api.get<StaffBankDetailResponse | null>(`/api/v1/hr/staff/${staffProfileId}/bank-details`);
}

export function upsertStaffBankDetails(staffProfileId: number, request: UpsertStaffBankDetailsRequest): Promise<StaffBankDetailResponse> {
	return api.post<StaffBankDetailResponse>(`/api/v1/hr/staff/${staffProfileId}/bank-details`, request);
}
