import { api } from "./client";

export interface IssueBookRequest {
	bookCopyId: number;
	borrowerPersonId: number;
	borrowedDate: string;
	dueDate: string;
}

export interface BorrowRecordResponse {
	id: number;
	bookCopyId: number;
	borrowerPersonId: number;
	borrowedDate: string;
	dueDate: string;
	returnedDate: string | null;
	status: string;
}

export function issueBook(request: IssueBookRequest): Promise<BorrowRecordResponse> {
	return api.post<BorrowRecordResponse>("/api/v1/library/borrow-records", request);
}

export function listActiveBorrowsByBorrower(borrowerPersonId: number): Promise<BorrowRecordResponse[]> {
	return api.get<BorrowRecordResponse[]>(`/api/v1/library/borrow-records?borrowerPersonId=${borrowerPersonId}`);
}

export function returnBook(id: number, returnedDate: string): Promise<BorrowRecordResponse> {
	return api.post<BorrowRecordResponse>(`/api/v1/library/borrow-records/${id}/return`, { returnedDate });
}

export function markBookLost(id: number): Promise<BorrowRecordResponse> {
	return api.post<BorrowRecordResponse>(`/api/v1/library/borrow-records/${id}/mark-lost`);
}
