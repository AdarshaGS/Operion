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
	items: SchoolTemplateCategoryStatus[];
	assetTypes: SchoolTemplateCategoryStatus[];
	leaveTypes: SchoolTemplateCategoryStatus[];
	classes: SchoolTemplateCategoryStatus[];
	sections: SchoolTemplateCategoryStatus[];
	subjects: SchoolTemplateCategoryStatus[];
	certificateTypes: SchoolTemplateCategoryStatus[];
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
	itemsCreated: string[];
	itemsSkipped: string[];
	assetTypesCreated: string[];
	assetTypesSkipped: string[];
	leaveTypesCreated: string[];
	leaveTypesSkipped: string[];
	classesCreated: string[];
	classesSkipped: string[];
	sectionsCreated: string[];
	sectionsSkipped: string[];
	subjectsCreated: string[];
	subjectsSkipped: string[];
	certificateTypesCreated: string[];
	certificateTypesSkipped: string[];
	/** Why no classes could be created (no academic year to put them in / no active campus); null otherwise. */
	classesNote: string | null;
	gradingScaleCreated: boolean;
	gradingScaleAlreadyExists: boolean;
}

/** Without an academic year the classes/sections are reported against the current year; with one, against that (open) year. */
export function getSchoolTemplatePreview(academicYearId?: number): Promise<SchoolTemplatePreviewResponse> {
	return api.get<SchoolTemplatePreviewResponse>(`/api/v1/organisations/settings/school-template/preview${yearQuery(academicYearId)}`);
}

function yearQuery(academicYearId?: number): string {
	return academicYearId === undefined ? "" : `?academicYearId=${academicYearId}`;
}

export interface StarterPackQuestion {
	key: string;
	label: string;
	helpText: string | null;
	defaultYes: boolean;
}

export interface StarterPackNeeds {
	questions: StarterPackQuestion[];
	/** False once the organisation has module settings - after that only the platform owner changes them. */
	canSelectModules: boolean;
}

export interface StarterPackModule {
	code: string;
	label: string;
	enabled: boolean;
	/** On because every school needs it, or because another selected module requires it. */
	locked: boolean;
	reason: string;
}

export type StarterPackAnswers = Record<string, boolean>;

export function getStarterPackNeeds(): Promise<StarterPackNeeds> {
	return api.get<StarterPackNeeds>("/api/v1/organisations/settings/school-template/needs");
}

/** Preview only - nothing is written. */
export function recommendStarterPack(answers: StarterPackAnswers): Promise<{ modules: StarterPackModule[] }> {
	return api.post<{ modules: StarterPackModule[] }>("/api/v1/organisations/settings/school-template/recommend", { answers });
}

/** With answers (first time only) the recommended modules are recorded too; without, only the template is applied.
 * With an academic year, its classes and sections are created in that (open) year instead of the current one. */
export function applySchoolTemplate(answers?: StarterPackAnswers, academicYearId?: number): Promise<SchoolTemplateApplyResponse> {
	return api.post<SchoolTemplateApplyResponse>(
		`/api/v1/organisations/settings/school-template/apply${yearQuery(academicYearId)}`,
		answers ? { answers } : undefined,
	);
}
