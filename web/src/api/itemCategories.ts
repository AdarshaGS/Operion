import { api } from "./client";

export interface CreateItemCategoryRequest {
	code: string;
	name: string;
	description?: string | null;
}

export interface ItemCategoryResponse {
	id: number;
	code: string;
	name: string;
	description: string | null;
	status: string;
}

export function createItemCategory(request: CreateItemCategoryRequest): Promise<ItemCategoryResponse> {
	return api.post<ItemCategoryResponse>("/api/v1/inventory/categories", request);
}

export function listItemCategories(): Promise<ItemCategoryResponse[]> {
	return api.get<ItemCategoryResponse[]>("/api/v1/inventory/categories");
}
