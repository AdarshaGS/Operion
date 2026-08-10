import { api } from "./client";

export interface RecordStockIssueRequest {
	itemId: number;
	campusId: number;
	quantity: number;
	issuedDate: string;
	issuedTo: string;
	purpose?: string | null;
	remarks?: string | null;
}

export interface StockIssueResponse {
	id: number;
	itemId: number;
	campusId: number;
	quantity: number;
	issuedDate: string;
	issuedTo: string;
	purpose: string | null;
	remarks: string | null;
}

export function recordStockIssue(request: RecordStockIssueRequest): Promise<StockIssueResponse> {
	return api.post<StockIssueResponse>("/api/v1/inventory/stock-issues", request);
}

export function listStockIssues(itemId: number, campusId: number): Promise<StockIssueResponse[]> {
	return api.get<StockIssueResponse[]>(`/api/v1/inventory/stock-issues?itemId=${itemId}&campusId=${campusId}`);
}
