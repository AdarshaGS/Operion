import { api } from "./client";

export type SchoolTemplateItemCategory =
	| "GRADE"
	| "DEPARTMENT"
	| "DESIGNATION"
	| "ROLE"
	| "FEE_CATEGORY"
	| "ITEM_CATEGORY"
	| "ITEM"
	| "ASSET_TYPE"
	| "CLASS"
	| "LEAVE_TYPE"
	| "SECTION"
	| "SUBJECT"
	| "GRADING_BAND"
	| "CERTIFICATE_TYPE";

export interface SchoolTemplateItemResponse {
	id: number;
	category: SchoolTemplateItemCategory;
	name: string;
	code: string | null;
	description: string | null;
	sequenceOrder: number | null;
	stage: string | null;
	categoryType: string | null;
	minPercentage: number | null;
	remark: string | null;
	permissionCodes: string[];
	unit: string | null;
	itemCategoryCode: string | null;
	/** False = marked "not required": kept in the catalog but ignored by preview and apply. */
	included: boolean;
}

/** Every field applies to some categories and not others - see SchoolTemplateItem's
 * (backend) class doc for which. Callers only set the ones relevant to the category
 * they're creating/editing and leave the rest undefined/null. */
export interface SaveSchoolTemplateItemRequest {
	name: string;
	code?: string | null;
	description?: string | null;
	sequenceOrder?: number | null;
	stage?: string | null;
	categoryType?: string | null;
	minPercentage?: number | null;
	remark?: string | null;
	permissionCodes?: string[];
	unit?: string | null;
	itemCategoryCode?: string | null;
}

export function listSchoolTemplateItems(category: SchoolTemplateItemCategory): Promise<SchoolTemplateItemResponse[]> {
	return api.get<SchoolTemplateItemResponse[]>(`/api/v1/organisations/settings/school-template/items/${category}`);
}

export function createSchoolTemplateItem(
	category: SchoolTemplateItemCategory,
	request: SaveSchoolTemplateItemRequest,
): Promise<SchoolTemplateItemResponse> {
	return api.post<SchoolTemplateItemResponse>(`/api/v1/organisations/settings/school-template/items/${category}`, request);
}

export function updateSchoolTemplateItem(id: number, request: SaveSchoolTemplateItemRequest): Promise<SchoolTemplateItemResponse> {
	return api.put<SchoolTemplateItemResponse>(`/api/v1/organisations/settings/school-template/items/${id}`, request);
}

export function deleteSchoolTemplateItem(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/organisations/settings/school-template/items/${id}`);
}

export function setSchoolTemplateItemIncluded(id: number, included: boolean): Promise<SchoolTemplateItemResponse> {
	return api.put<SchoolTemplateItemResponse>(`/api/v1/organisations/settings/school-template/items/${id}/included`, { included });
}

/** Select all / none for one category. */
export function setSchoolTemplateCategoryIncluded(
	category: SchoolTemplateItemCategory,
	included: boolean,
): Promise<SchoolTemplateItemResponse[]> {
	return api.put<SchoolTemplateItemResponse[]>(
		`/api/v1/organisations/settings/school-template/items/category/${category}/included`,
		{ included },
	);
}
