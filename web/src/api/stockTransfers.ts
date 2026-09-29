import { api } from "./client";

export interface RecordStockTransferRequest {
	itemId: number;
	fromCampusId: number;
	toCampusId: number;
	quantity: number;
	transferDate: string;
	remarks?: string | null;
}

export interface StockTransferResponse {
	id: number;
	itemId: number;
	fromCampusId: number;
	toCampusId: number;
	quantity: number;
	transferDate: string;
	remarks: string | null;
}

export function recordStockTransfer(request: RecordStockTransferRequest): Promise<StockTransferResponse> {
	return api.post<StockTransferResponse>("/api/v1/inventory/stock-transfers", request);
}

export function listStockTransfers(itemId: number, campusId: number): Promise<StockTransferResponse[]> {
	return api.get<StockTransferResponse[]>(`/api/v1/inventory/stock-transfers?itemId=${itemId}&campusId=${campusId}`);
}
