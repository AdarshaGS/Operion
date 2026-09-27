import { api } from "./client";

export interface CreateBookRequest {
	isbn?: string | null;
	title: string;
	author?: string | null;
	publisher?: string | null;
	category?: string | null;
	edition?: string | null;
}

export interface UpdateBookRequest {
	isbn?: string | null;
	title: string;
	author?: string | null;
	publisher?: string | null;
	category?: string | null;
	edition?: string | null;
}

export interface BookResponse {
	id: number;
	isbn: string | null;
	title: string;
	author: string | null;
	publisher: string | null;
	category: string | null;
	edition: string | null;
	status: string;
}

export function createBook(request: CreateBookRequest): Promise<BookResponse> {
	return api.post<BookResponse>("/api/v1/library/books", request);
}

export function updateBook(id: number, request: UpdateBookRequest): Promise<BookResponse> {
	return api.put<BookResponse>(`/api/v1/library/books/${id}`, request);
}

export function listBooks(): Promise<BookResponse[]> {
	return api.get<BookResponse[]>("/api/v1/library/books");
}

export interface AddBookCopyRequest {
	campusId: number;
	accessionNumber: string;
	acquiredDate?: string | null;
	shelfLocation?: string | null;
}

export interface BookCopyResponse {
	id: number;
	bookId: number;
	campusId: number;
	accessionNumber: string;
	status: string;
	acquiredDate: string | null;
	shelfLocation: string | null;
}

export function addBookCopy(bookId: number, request: AddBookCopyRequest): Promise<BookCopyResponse> {
	return api.post<BookCopyResponse>(`/api/v1/library/books/${bookId}/copies`, request);
}

export function listBookCopies(bookId: number): Promise<BookCopyResponse[]> {
	return api.get<BookCopyResponse[]>(`/api/v1/library/books/${bookId}/copies`);
}

export function updateCopyShelfLocation(bookId: number, copyId: number, shelfLocation: string | null): Promise<BookCopyResponse> {
	return api.put<BookCopyResponse>(`/api/v1/library/books/${bookId}/copies/${copyId}/shelf-location`, { shelfLocation });
}

export interface LibraryStatusSummaryResponse {
	available: number;
	issued: number;
	overdue: number;
	lost: number;
	damaged: number;
}

export function getLibrarySummary(): Promise<LibraryStatusSummaryResponse> {
	return api.get<LibraryStatusSummaryResponse>("/api/v1/library/summary");
}
