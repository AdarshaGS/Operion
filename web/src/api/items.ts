import { api } from "./client";

export interface CreateItemRequest {
	categoryId: number;
	code: string;
	name: string;
	unit: string;
	description?: string | null;
}

export interface ItemResponse {
	id: number;
	categoryId: number;
	code: string;
	name: string;
	unit: string;
	description: string | null;
	status: string;
}

export interface BalanceResponse {
	itemId: number;
	campusId: number;
	balance: number;
}

export function createItem(request: CreateItemRequest): Promise<ItemResponse> {
	return api.post<ItemResponse>("/api/v1/inventory/items", request);
}

export function listItems(): Promise<ItemResponse[]> {
	return api.get<ItemResponse[]>("/api/v1/inventory/items");
}

export function getItemBalance(itemId: number, campusId: number): Promise<BalanceResponse> {
	return api.get<BalanceResponse>(`/api/v1/inventory/items/${itemId}/balance?campusId=${campusId}`);
}
