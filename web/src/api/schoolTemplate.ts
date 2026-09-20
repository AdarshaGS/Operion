import { api } from "./client";

export interface SchoolTemplateCategoryStatus {
	name: string;
	alreadyExists: boolean;
}

export interface SchoolTemplatePreviewResponse {
	grades: SchoolTemplateCategoryStatus[];
	departments: SchoolTemplateCategoryStatus[];
	designations: SchoolTemplateCategoryStatus[];
	roles: SchoolTemplateCategoryStatus[];
	feeCategories: SchoolTemplateCategoryStatus[];
	itemCategories: SchoolTemplateCategoryStatus[];
	gradingScaleExists: boolean;
}

export interface SchoolTemplateApplyResponse {
	gradesCreated: string[];
	gradesSkipped: string[];
	departmentsCreated: string[];
	departmentsSkipped: string[];
	designationsCreated: string[];
	designationsSkipped: string[];
	rolesCreated: string[];
	rolesSkipped: string[];
	feeCategoriesCreated: string[];
	feeCategoriesSkipped: string[];
	itemCategoriesCreated: string[];
	itemCategoriesSkipped: string[];
	gradingScaleCreated: boolean;
	gradingScaleAlreadyExists: boolean;
}

export function getSchoolTemplatePreview(): Promise<SchoolTemplatePreviewResponse> {
	return api.get<SchoolTemplatePreviewResponse>("/api/v1/organisations/settings/school-template/preview");
}

export function applySchoolTemplate(): Promise<SchoolTemplateApplyResponse> {
	return api.post<SchoolTemplateApplyResponse>("/api/v1/organisations/settings/school-template/apply");
}
