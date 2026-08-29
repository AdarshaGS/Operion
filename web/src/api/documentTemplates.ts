import { api } from "./client";

export type DocumentType = "QUESTION_PAPER_HEADER" | "REPORT_CARD";
export type TemplateStyle = "CLASSIC" | "MODERN" | "MINIMAL" | "ELEGANT";

export interface DocumentTemplateResponse {
	documentType: DocumentType;
	templateStyle: TemplateStyle;
	pageSize: string;
	fontStyle: string;
	fontSize: number;
	headerSubtext: string | null;
	/** false when this document type has never been saved - the fields are defaults, not a persisted row. */
	configured: boolean;
}

export interface UpsertDocumentTemplateRequest {
	templateStyle: TemplateStyle;
	pageSize: string;
	fontStyle: string;
	fontSize: number;
	headerSubtext: string | null;
}

export function getDocumentTemplate(documentType: DocumentType): Promise<DocumentTemplateResponse> {
	return api.get<DocumentTemplateResponse>(`/api/v1/document-templates/${documentType}`);
}

export function updateDocumentTemplate(
	documentType: DocumentType,
	request: UpsertDocumentTemplateRequest,
): Promise<DocumentTemplateResponse> {
	return api.put<DocumentTemplateResponse>(`/api/v1/document-templates/${documentType}`, request);
}
