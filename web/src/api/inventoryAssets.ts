import { api } from "./client";

export interface CreateInventoryAssetRequest {
	itemId: number;
	campusId: number;
	assetTag: string;
	purchaseDate?: string | null;
	warrantyExpiryDate?: string | null;
}

export interface InventoryAssetResponse {
	id: number;
	itemId: number;
	itemName: string;
	campusId: number;
	assetTag: string;
	assignedPersonId: number | null;
	assignedPersonName: string | null;
	assignedLocation: string | null;
	purchaseDate: string | null;
	warrantyExpiryDate: string | null;
	status: string;
}

export function createInventoryAsset(request: CreateInventoryAssetRequest): Promise<InventoryAssetResponse> {
	return api.post<InventoryAssetResponse>("/api/v1/inventory/assets", request);
}

export function listInventoryAssets(campusId: number): Promise<InventoryAssetResponse[]> {
	return api.get<InventoryAssetResponse[]>(`/api/v1/inventory/assets?campusId=${campusId}`);
}

export function changeInventoryAssetStatus(id: number, status: string): Promise<InventoryAssetResponse> {
	return api.post<InventoryAssetResponse>(`/api/v1/inventory/assets/${id}/status`, { status });
}

export function assignInventoryAsset(
	id: number,
	assignedPersonId: number | null,
	assignedLocation: string | null,
): Promise<InventoryAssetResponse> {
	return api.post<InventoryAssetResponse>(`/api/v1/inventory/assets/${id}/assign`, { assignedPersonId, assignedLocation });
}
