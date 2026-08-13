import { platformApi } from "./platformClient";

export interface PlatformInvoiceResponse {
	id: number;
	organisationId: number;
	subscriptionId: number;
	periodStart: string;
	periodEnd: string;
	studentCountAtBilling: number;
	amount: number;
	status: string;
	issuedAt: string;
	dueDate: string;
	paidAt: string | null;
}

export interface GenerateInvoiceRequest {
	periodStart: string;
	periodEnd: string;
	dueDate: string;
}

export function listInvoices(organisationId: number): Promise<PlatformInvoiceResponse[]> {
	return platformApi.get<PlatformInvoiceResponse[]>(`/api/v1/platform/organisations/${organisationId}/invoices`);
}

export function generateInvoice(organisationId: number, request: GenerateInvoiceRequest): Promise<PlatformInvoiceResponse> {
	return platformApi.post<PlatformInvoiceResponse>(`/api/v1/platform/organisations/${organisationId}/invoices/generate`, request);
}

export function markInvoicePaid(id: number): Promise<PlatformInvoiceResponse> {
	return platformApi.post<PlatformInvoiceResponse>(`/api/v1/platform/invoices/${id}/mark-paid`, {});
}

export function listAllInvoices(): Promise<PlatformInvoiceResponse[]> {
	return platformApi.get<PlatformInvoiceResponse[]>("/api/v1/platform/invoices");
}
