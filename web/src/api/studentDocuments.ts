import { api } from "./client";

export interface UploadStudentDocumentRequest {
	documentType: string;
	fileReference: string;
	fileName: string;
	mimeType: string;
}

export interface StudentDocumentResponse {
	id: number;
	studentId: number;
	documentType: string;
	fileReference: string;
	fileUrl: string;
	fileName: string;
	mimeType: string | null;
	verificationStatus: string;
	verifiedBy: number | null;
	verifiedAt: string | null;
	status: string;
}

export function uploadStudentDocument(studentId: number, request: UploadStudentDocumentRequest): Promise<StudentDocumentResponse> {
	return api.post<StudentDocumentResponse>(`/api/v1/students/${studentId}/documents`, request);
}

export function listStudentDocuments(studentId: number): Promise<StudentDocumentResponse[]> {
	return api.get<StudentDocumentResponse[]>(`/api/v1/students/${studentId}/documents`);
}

export function verifyStudentDocument(
	studentId: number,
	documentId: number,
	verificationStatus: "VERIFIED" | "REJECTED",
	verifiedBy: number,
): Promise<StudentDocumentResponse> {
	return api.patch<StudentDocumentResponse>(`/api/v1/students/${studentId}/documents/${documentId}/verify`, {
		verificationStatus,
		verifiedBy,
	});
}
