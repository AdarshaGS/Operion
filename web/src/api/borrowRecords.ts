import { api } from "./client";

export interface IssueBookRequest {
	bookCopyId: number;
	borrowerPersonId: number;
	borrowedDate: string;
	dueDate?: string | null;
}

export interface BorrowRecordResponse {
	id: number;
	bookCopyId: number;
	borrowerPersonId: number;
	borrowedDate: string;
	dueDate: string;
	returnedDate: string | null;
	status: string;
	borrowerName: string;
	bookTitle: string;
	accessionNumber: string;
}

export interface OverdueBorrowResponse {
	id: number;
	borrowerPersonId: number;
	borrowerName: string;
	borrowerPhone: string | null;
	borrowerEmail: string | null;
	bookTitle: string;
	accessionNumber: string;
	dueDate: string;
	daysOverdue: number;
	fineDue: number;
}

export function issueBook(request: IssueBookRequest): Promise<BorrowRecordResponse> {
	return api.post<BorrowRecordResponse>("/api/v1/library/borrow-records", request);
}

export function listActiveBorrows(): Promise<BorrowRecordResponse[]> {
	return api.get<BorrowRecordResponse[]>("/api/v1/library/borrow-records");
}

export function listActiveBorrowsByBorrower(borrowerPersonId: number): Promise<BorrowRecordResponse[]> {
	return api.get<BorrowRecordResponse[]>(`/api/v1/library/borrow-records?borrowerPersonId=${borrowerPersonId}`);
}

export function listBorrowHistory(borrowerPersonId: number): Promise<BorrowRecordResponse[]> {
	return api.get<BorrowRecordResponse[]>(`/api/v1/library/borrow-records/by-borrower/${borrowerPersonId}`);
}

export function listOverdueBorrows(): Promise<OverdueBorrowResponse[]> {
	return api.get<OverdueBorrowResponse[]>("/api/v1/library/borrow-records/overdue");
}

export function returnBook(id: number, returnedDate: string, damaged = false): Promise<BorrowRecordResponse> {
	return api.post<BorrowRecordResponse>(`/api/v1/library/borrow-records/${id}/return`, { returnedDate, damaged });
}

export function markBookLost(id: number): Promise<BorrowRecordResponse> {
	return api.post<BorrowRecordResponse>(`/api/v1/library/borrow-records/${id}/mark-lost`);
}

export function markBookDamaged(id: number): Promise<BorrowRecordResponse> {
	return api.post<BorrowRecordResponse>(`/api/v1/library/borrow-records/${id}/mark-damaged`);
}

export function sendOverdueReminder(id: number): Promise<number> {
	return api.post<number>(`/api/v1/library/borrow-records/${id}/send-reminder`);
}
