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

export interface OverdueInvoiceResponse {
	id: number;
	invoiceNumber: string;
	studentEnrollmentId: number;
	studentId: number;
	schoolClassId: number;
	totalAmount: number;
	amountPaid: number;
	outstanding: number;
	dueDate: string;
	daysOverdue: number;
}

export function listOverdueInvoices(filters: { schoolClassId?: number; studentEnrollmentId?: number } = {}): Promise<OverdueInvoiceResponse[]> {
	const params = new URLSearchParams();
	if (filters.schoolClassId) params.set("schoolClassId", String(filters.schoolClassId));
	if (filters.studentEnrollmentId) params.set("studentEnrollmentId", String(filters.studentEnrollmentId));
	const query = params.toString();
	return api.get<OverdueInvoiceResponse[]>(`/api/v1/fees/invoices/overdue${query ? `?${query}` : ""}`);
}

export interface ReminderResult {
	invoiceId: number;
	recipientsNotified: number;
}

export function sendReminders(invoiceIds: number[]): Promise<ReminderResult[]> {
	return api.post<ReminderResult[]>("/api/v1/fees/invoices/send-reminders", { invoiceIds });
}
