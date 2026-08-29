import { api } from "./client";

export type IdCardElementType = "TEXT" | "DATA_FIELD" | "PHOTO" | "QR_CODE" | "HEADER_BAND" | "DIVIDER";

/** One element in an IdCardTemplate's layoutJson. Positions/sizes are in mm, relative to
 * the card's top-left corner - matches widthMm/heightMm on the template itself. */
export interface IdCardElement {
	id: string;
	type: IdCardElementType;
	x: number;
	y: number;
	width: number;
	height: number;
	/** TEXT only - the static label shown on the card. */
	text?: string;
	/** DATA_FIELD/QR_CODE only - which student attribute this element is bound to. */
	field?: string;
	fontSize?: number;
}

export interface IdCardLayout {
	elements: IdCardElement[];
}

export interface IdCardTemplateResponse {
	id: number;
	name: string;
	widthMm: number;
	heightMm: number;
	layoutJson: string;
}

export interface CreateIdCardTemplateRequest {
	name: string;
	widthMm: number;
	heightMm: number;
	layoutJson: string;
}

export interface RenderedIdCardElement {
	id: string;
	type: IdCardElementType;
	x: number;
	y: number;
	width: number;
	height: number;
	value: string | null;
	photoUrl: string | null;
}

export interface IdCardRenderResponse {
	templateId: number;
	studentId: string;
	widthMm: number;
	heightMm: number;
	elements: RenderedIdCardElement[];
}

export function listIdCardTemplates(): Promise<IdCardTemplateResponse[]> {
	return api.get<IdCardTemplateResponse[]>("/api/v1/id-card-templates");
}

export function createIdCardTemplate(request: CreateIdCardTemplateRequest): Promise<IdCardTemplateResponse> {
	return api.post<IdCardTemplateResponse>("/api/v1/id-card-templates", request);
}

export function renderIdCardTemplate(templateId: number, studentId: number): Promise<IdCardRenderResponse> {
	return api.post<IdCardRenderResponse>(`/api/v1/id-card-templates/${templateId}/render?studentId=${studentId}`);
}
