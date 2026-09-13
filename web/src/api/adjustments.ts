import { api } from "./client";

export interface RecordAdjustmentRequest {
	invoiceId: number;
	amount: number;
	reason: string;
	approvedBy: number;
	adjustmentDate: string;
}

export interface AdjustmentResponse {
	id: number;
	invoiceId: number;
	amount: number;
	reason: string;
	approvedBy: number;
	adjustmentDate: string;
}

export function recordAdjustment(request: RecordAdjustmentRequest): Promise<AdjustmentResponse> {
	return api.post<AdjustmentResponse>("/api/v1/fees/adjustments", request);
}

export function listAdjustments(invoiceId: number): Promise<AdjustmentResponse[]> {
	return api.get<AdjustmentResponse[]>(`/api/v1/fees/adjustments?invoiceId=${invoiceId}`);
}
