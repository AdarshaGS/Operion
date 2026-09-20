import { api } from "./client";

export interface CreateFeeCategoryRequest {
	code: string;
	name: string;
	description?: string | null;
	categoryType: string;
}

export interface FeeCategoryResponse {
	id: number;
	code: string;
	name: string;
	description: string | null;
	status: string;
	categoryType: string;
}

export function createFeeCategory(request: CreateFeeCategoryRequest): Promise<FeeCategoryResponse> {
	return api.post<FeeCategoryResponse>("/api/v1/fees/categories", request);
}

export function listFeeCategories(): Promise<FeeCategoryResponse[]> {
	return api.get<FeeCategoryResponse[]>("/api/v1/fees/categories");
}
