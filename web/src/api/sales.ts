import { api } from "./client";

export interface SaleLineItemRequest {
	itemId: number;
	quantity: number;
	unitPrice: number;
}

export interface CreateSaleRequest {
	customerId: number;
	campusId: number;
	saleDate: string;
	lines: SaleLineItemRequest[];
}

export interface SaleResponse {
	id: number;
	customerId: number;
	campusId: number;
	receiptNumber: string;
	saleDate: string;
	totalAmount: number;
	amountPaid: number;
	status: string;
}

export interface SaleLineResponse {
	id: number;
	itemId: number;
	quantity: number;
	unitPrice: number;
	lineTotal: number;
}

export interface SalePaymentResponse {
	id: number;
	paymentMethod: string;
	amount: number;
	paidAt: string;
}

export interface SaleDetailResponse extends SaleResponse {
	lines: SaleLineResponse[];
	payments: SalePaymentResponse[];
}

export interface RecordSalePaymentRequest {
	paymentMethod: string;
	amount: number;
	paidAt: string;
}

export function createSale(request: CreateSaleRequest): Promise<SaleResponse> {
	return api.post<SaleResponse>("/api/v1/sales", request);
}

export function listSales(customerId?: number): Promise<SaleResponse[]> {
	return api.get<SaleResponse[]>(customerId != null ? `/api/v1/sales?customerId=${customerId}` : "/api/v1/sales");
}

export function getSale(id: number): Promise<SaleDetailResponse> {
	return api.get<SaleDetailResponse>(`/api/v1/sales/${id}`);
}

export function recordSalePayment(id: number, request: RecordSalePaymentRequest): Promise<SaleDetailResponse> {
	return api.post<SaleDetailResponse>(`/api/v1/sales/${id}/payments`, request);
}
