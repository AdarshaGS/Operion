import { api } from "./client";

export interface RecordRefundRequest {
	paymentId: number;
	invoiceId: number;
	amount: number;
	reason: string;
	approvedBy: number;
	refundDate: string;
}

export interface RefundResponse {
	id: number;
	paymentId: number;
	invoiceId: number;
	amount: number;
	reason: string;
	approvedBy: number;
	refundDate: string;
}

export function recordRefund(request: RecordRefundRequest): Promise<RefundResponse> {
	return api.post<RefundResponse>("/api/v1/fees/refunds", request);
}
