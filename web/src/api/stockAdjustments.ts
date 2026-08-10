import { api } from "./client";

export interface RecordStockAdjustmentRequest {
	itemId: number;
	campusId: number;
	quantityDelta: number;
	reason: string;
	adjustmentDate: string;
	remarks?: string | null;
}

export interface StockAdjustmentResponse {
	id: number;
	itemId: number;
	campusId: number;
	quantityDelta: number;
	reason: string;
	adjustmentDate: string;
	remarks: string | null;
}

export function recordStockAdjustment(request: RecordStockAdjustmentRequest): Promise<StockAdjustmentResponse> {
	return api.post<StockAdjustmentResponse>("/api/v1/inventory/stock-adjustments", request);
}

export function listStockAdjustments(itemId: number, campusId: number): Promise<StockAdjustmentResponse[]> {
	return api.get<StockAdjustmentResponse[]>(`/api/v1/inventory/stock-adjustments?itemId=${itemId}&campusId=${campusId}`);
}
