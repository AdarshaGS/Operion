import { api } from "./client";

export interface InvoiceResponse {
	id: number;
	academicYearId: number;
	studentFeeAssignmentId: number;
	feeStructureInstallmentId: number;
	invoiceNumber: string;
	totalAmount: number;
	amountPaid: number;
	outstanding: number;
	dueDate: string;
	status: string;
}

export function generateInvoice(assignmentId: number, feeStructureInstallmentId: number): Promise<InvoiceResponse> {
	return api.post<InvoiceResponse>(`/api/v1/fees/assignments/${assignmentId}/invoices`, { feeStructureInstallmentId });
}

export function listInvoices(studentEnrollmentId: number): Promise<InvoiceResponse[]> {
	return api.get<InvoiceResponse[]>(`/api/v1/fees/invoices?studentEnrollmentId=${studentEnrollmentId}`);
}
