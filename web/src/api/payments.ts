import { api } from "./client";

export interface AllocationEntry {
	invoiceId: number;
	amount: number;
}

export interface RecordPaymentRequest {
	academicYearId: number;
	amount: number;
	paymentMethod: string;
	paymentDate: string;
	remarks?: string | null;
	allocations: AllocationEntry[];
}

export interface PaymentResponse {
	id: number;
	academicYearId: number;
	receiptNumber: string;
	amount: number;
	paymentMethod: string;
	paymentDate: string;
	status: string;
	remarks: string | null;
}

export function recordPayment(request: RecordPaymentRequest): Promise<PaymentResponse> {
	return api.post<PaymentResponse>("/api/v1/fees/payments", request);
}
