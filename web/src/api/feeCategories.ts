import { api } from "./client";

export interface CreateFeeCategoryRequest {
	code: string;
	name: string;
	description?: string | null;
	categoryType: string;
	/** Optional; the backend defaults it to false. */
	requiredAtAdmission?: boolean;
}

export interface FeeCategoryResponse {
	id: number;
	code: string;
	name: string;
	description: string | null;
	status: string;
	categoryType: string;
	requiredAtAdmission: boolean;
}

export function createFeeCategory(request: CreateFeeCategoryRequest): Promise<FeeCategoryResponse> {
	return api.post<FeeCategoryResponse>("/api/v1/fees/categories", request);
}

export function listFeeCategories(): Promise<FeeCategoryResponse[]> {
	return api.get<FeeCategoryResponse[]>("/api/v1/fees/categories");
}

/** Flips whether the category's first instalment is invoiced at enrolment. Needs FEE_CATEGORY_MANAGE. */
export function updateFeeCategoryAdmission(id: number, requiredAtAdmission: boolean): Promise<FeeCategoryResponse> {
	return api.put<FeeCategoryResponse>(`/api/v1/fees/categories/${id}/admission`, { requiredAtAdmission });
}
