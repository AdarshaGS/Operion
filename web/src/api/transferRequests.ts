import { api } from "./client";

export interface RaiseTransferRequestRequest {
	toCampusId: number;
	reason?: string | null;
}

export interface TransferRequestResponse {
	id: number;
	studentId: number;
	fromCampusId: number;
	toCampusId: number;
	reason: string | null;
	status: string;
	requestedBy: number;
	decidedBy: number | null;
	decidedAt: string | null;
}

export function raiseTransferRequest(studentId: number, request: RaiseTransferRequestRequest): Promise<TransferRequestResponse> {
	return api.post<TransferRequestResponse>(`/api/v1/students/${studentId}/transfer-requests`, request);
}

/** Pass studentId for one student's history, status for an org-wide inbox (e.g. all
 * PENDING requests), or both together to scope by student and status at once. */
export function listTransferRequests(filter: { studentId?: number; status?: string }): Promise<TransferRequestResponse[]> {
	const params = new URLSearchParams();
	if (filter.studentId) params.set("studentId", String(filter.studentId));
	if (filter.status) params.set("status", filter.status);
	return api.get<TransferRequestResponse[]>(`/api/v1/transfer-requests?${params.toString()}`);
}

export function approveTransferRequest(id: number): Promise<TransferRequestResponse> {
	return api.post<TransferRequestResponse>(`/api/v1/transfer-requests/${id}/approve`);
}

export function rejectTransferRequest(id: number): Promise<TransferRequestResponse> {
	return api.post<TransferRequestResponse>(`/api/v1/transfer-requests/${id}/reject`);
}
