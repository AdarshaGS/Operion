import { api } from "./client";

export interface RecordStockEntryRequest {
	itemId: number;
	campusId: number;
	quantity: number;
	unitCost?: number | null;
	entryDate: string;
	source?: string | null;
	remarks?: string | null;
}

export interface StockEntryResponse {
	id: number;
	itemId: number;
	campusId: number;
	quantity: number;
	unitCost: number | null;
	entryDate: string;
	source: string | null;
	remarks: string | null;
}

export function recordStockEntry(request: RecordStockEntryRequest): Promise<StockEntryResponse> {
	return api.post<StockEntryResponse>("/api/v1/inventory/stock-entries", request);
}

export function listStockEntries(itemId: number, campusId: number): Promise<StockEntryResponse[]> {
	return api.get<StockEntryResponse[]>(`/api/v1/inventory/stock-entries?itemId=${itemId}&campusId=${campusId}`);
}
